package agh.bridge.back

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.actor.typed.scaladsl.AskPattern._

import agh.bridge.core.PlayerDirection
import agh.bridge.core as Core

object Backend {
  type Actor = ActorRef[Command]

  case object SessionNotFound
  type SessionNotFound = SessionNotFound.type

  case object UserNotInSession
  type UserNotInSession = UserNotInSession.type

  import Session.SessionFull
  import Session.IllegalAction

  sealed trait Command

  sealed trait UserCommand extends Command
  final case class Heartbeat(userId: User.Id) extends UserCommand
  final case class FindSession(userId: User.Id, replyTo: ActorRef[Either[SessionNotFound, Session.Id]]) extends UserCommand
  final case class LeaveSession(userId: User.Id, replyTo: ActorRef[Unit]) extends UserCommand
  final case class CreateLobby(hostId: User.Id, replyTo: ActorRef[Session.Id]) extends UserCommand
  final case class JoinLobby(userId: User.Id, sessionId: Session.Id, replyTo: ActorRef[Either[SessionNotFound | SessionFull, Unit]]) extends UserCommand
  final case class SetUserReady(userId: User.Id, ready: Boolean, replyTo: ActorRef[Either[UserNotInSession, Unit]]) extends UserCommand
  final case class KickUser(userId: User.Id, kickId: User.Id, replyTo: ActorRef[Either[UserNotInSession, Unit]]) extends UserCommand
  final case class PlayAction(userId: User.Id, action: Core.Action, replyTo: ActorRef[Either[UserNotInSession | IllegalAction, Unit]]) extends UserCommand
  final case class SetUserAsAssistant(userId: User.Id, replyTo: ActorRef[Either[UserNotInSession, Unit]]) extends UserCommand

  sealed trait SessionCommand extends Command
  final case class GetLobbyInfo(userId: User.Id, sessionId: Session.Id, replyTo: ActorRef[Either[SessionNotFound, Session.SessionInfo]]) extends SessionCommand
  final case class SubscribeToSessionInfo(sessionId: Session.Id, userId: User.Id, subscriber: ActorRef[Session.SessionInfo]) extends SessionCommand
  final case class AddAssistant(sessionId: Session.Id, position: PlayerDirection, replyTo: ActorRef[Either[SessionNotFound | SessionFull, Unit]]) extends SessionCommand

  sealed trait LobbyCommand extends SessionCommand
  final case class ForceSwap(sessionId: Session.Id, first: PlayerDirection, second: PlayerDirection, replyTo: ActorRef[Either[SessionNotFound, Unit]]) extends LobbyCommand
  final case class SetAssistantLevel(sessionId: Session.Id, level: Int, replyTo: ActorRef[Either[SessionNotFound, Unit]]) extends LobbyCommand

  private final case class UserDied(userId: User.Id) extends Command
  private final case class SessionDied(sessionId: Session.Id) extends Command

  private final case class HostJoinSessionResponse(
    sessionId: Session.Id,
    session: Session.Actor,
    createLobbyInitiator: ActorRef[Session.Id],
    result: Either[SessionFull, Unit]
  ) extends Command

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    context.setLoggerName("agh.bridge.back.Backend")
    val users: Map[User.Id, User.Actor] =
      Map.empty.withDefault { id => 
        var user = context.spawn(User(id), s"user-$id") 
        context.watchWith(user, UserDied(id))
        user
      }
    val sessions: Map[Session.Id, Session.Actor] = Map.empty
    backend(users, sessions)
  }

  private def backend(
    users: Map[User.Id, User.Actor],
    sessions: Map[Session.Id, Session.Actor],
  ): Behavior[Command] = Behaviors.setup { context =>
    given ActorSystem[_] = context.system
    given ExecutionContext = context.executionContext
    given akka.util.Timeout = akka.util.Timeout(1000, java.util.concurrent.TimeUnit.MILLISECONDS)

    Behaviors.receiveMessage {

      case Heartbeat(userId) =>
        context.log.debug("[backend] Heartbeat({})", userId)
        val user = users(userId)
        user ! User.Heartbeat
        if (users contains userId) Behaviors.same
        else backend(users + (userId -> user), sessions)

      case FindSession(userId, replyTo) =>
        context.log.debug("[backend] FindSession({})", userId)
        val user = users(userId)
        val sessionOptFut = user.ask[Option[Session.Actor]](User.GetSession(_))
        val sessionIdOptFut = sessionOptFut flatMap {
          case Some(session) => session.ask[Session.Id](Session.GetId(_)) map Some.apply
          case None => Future.successful(None)
        }
        sessionIdOptFut.map (replyTo ! _.toRight(SessionNotFound))
        if (users contains userId) Behaviors.same
        else backend(users + (userId -> user), sessions)

      case CreateLobby(hostId, replyTo) =>
        context.log.debug("[backend] CreateLobby({})", hostId)
        val host = users(hostId)
        val sessionId = java.util.UUID.randomUUID.toString
        val session = context.spawn(Session(sessionId), s"session-$sessionId")
        context.watchWith(session, SessionDied(sessionId))
        val adapter = context.messageAdapter[Either[SessionFull, Unit]](
          HostJoinSessionResponse(sessionId, session, replyTo, _))
        host ! User.JoinSession(session, adapter)
        if (users contains hostId) Behaviors.same
        else backend(users + (hostId -> host), sessions)

      case HostJoinSessionResponse(sessionId, session, replyTo, Right(())) =>
        context.log.debug("[backend] HostJoinSessionResponse: success")
        replyTo ! sessionId
        backend(users, sessions + (sessionId -> session))

      case HostJoinSessionResponse(_, _, _, Left(SessionFull)) =>
        context.log.debug("[backend] HostJoinSessionResponse: unexpected SessionFull")
        Behaviors.unhandled

      case JoinLobby(userId, sessionId, replyTo) =>
        context.log.debug("[backend] JoinLobby({}, {})", sessionId, userId)
        val user = users(userId)
        val sessionOpt = sessions.get(sessionId)
        val joinResultFut = sessionOpt match {
          case Some(session) => user.ask[Either[SessionFull, Unit]](User.JoinSession(session, _))
          case None => Future.successful(Left(SessionNotFound))
        }
        joinResultFut map (replyTo ! _)
        if (users contains userId) Behaviors.same
        else backend(users + (userId -> user), sessions)

      case LeaveSession(userId, replyTo) =>
        context.log.debug("[backend] LeaveSession({})", userId)
        val user = users(userId)
        user ! User.LeaveSession(replyTo)
        if (users contains userId) Behaviors.same
        else backend(users + (userId -> user), sessions)

      case GetLobbyInfo(userId, sessionId, replyTo) =>
        context.log.debug("[backend] GetLobbyInfo({})", sessionId)
        val user = users(userId)
        val sessionOpt = sessions.get(sessionId).toRight(SessionNotFound)
        val lobbyInfoOptFut = sessionOpt match {
          case Right(session) => session.ask[Session.SessionInfo](Session.GetInfo(user, _)) map Right.apply
          case Left(_) => Future.successful(Left(SessionNotFound))
        }
        lobbyInfoOptFut map (replyTo ! _)
        if (users contains userId) Behaviors.same
        else backend(users + (userId -> user), sessions)

      case ForceSwap(sessionId, first, second, replyTo) =>
        context.log.debug("[backend] ForceSwap({}, {}, {})", sessionId, first, second)
        val sessionOpt = sessions.get(sessionId)
        val swapResultFut = sessionOpt match {
          case Some(session) => session.ask[Unit](Session.ForceSwap(first, second, _)).map(Right.apply)
          case None => Future.successful(Left(SessionNotFound))
        }
        swapResultFut map (replyTo ! _)
        Behaviors.same

      case SetAssistantLevel(sessionId, level, replyTo) =>
        context.log.debug("[backend] SetAssistantLevel({}, {})", sessionId, level)
        val sessionOpt = sessions.get(sessionId)
        val setLevelResultFut = sessionOpt match {
          case Some(session) => session.ask[Unit](Session.SetAssistantLevel(level, _)).map(Right.apply)
          case None => Future.successful(Left(SessionNotFound))
        }
        setLevelResultFut map (replyTo ! _)
        Behaviors.same

      case SetUserReady(userId, ready, replyTo) =>
        context.log.debug("[backend] SetUserReady({}, {})", userId, ready)
        val user = users(userId)
        for
          session <- user.ask[Option[Session.Actor]](User.GetSession(_))
        do
          val res = session match {
            case Some(session) => session.ask[Unit](Session.SetUserReady(user, ready, _)).map(Right.apply)
            case None => Future.successful(Left(UserNotInSession))
          }
          res map (replyTo ! _)
        if (users contains userId) Behaviors.same
        else backend(users + (userId -> user), sessions)

      case KickUser(userId, kickId, replyTo) =>
        context.log.debug("[backend] KickUser({}, {})", userId, kickId)
        val kicker = users(userId)
        val kicked = users(kickId)
        for
          session <- kicker.ask[Option[Session.Actor]](User.GetSession(_))
        do
          val res = session match {
            case Some(session) => session.ask[Unit](Session.KickUser(kicker, kicked, _)).map(Right.apply)
            case None => Future.successful(Left(UserNotInSession))
          }
          res map (replyTo ! _)
        if (users contains userId) Behaviors.same
        else backend(users + (userId -> kicker), sessions)

      case PlayAction(userId, action, replyTo) =>
        context.log.debug("[backend] PlayAction({}, {})", userId, action)
        val user = users(userId)
        for
          session <- user.ask[Option[Session.Actor]](User.GetSession(_))
        do
          val res = session match {
            case Some(session) => session.ask[Either[IllegalAction, Unit]](Session.PlayAction(user, action, _))
            case None => Future.successful(Left(UserNotInSession))
          }
          res map (replyTo ! _)
        if (users contains userId) Behaviors.same
        else backend(users + (userId -> user), sessions)

      case UserDied(userId) =>
        context.log.debug("[backend] UserDied({})", userId)
        backend(users - userId, sessions)

      case SessionDied(sessionId) =>
        context.log.debug("[backend] SessionDied({})", sessionId)
        backend(users, sessions - sessionId)

      case SubscribeToSessionInfo(sessionId, userId, subscriber) =>
        context.log.debug("[backend] SubscribeToSessionInfo({})", sessionId)
        val user = users(userId)
        val sessionOpt = sessions.get(sessionId).toRight(SessionNotFound)
        sessionOpt.map(_ ! Session.AddSubscriber(user, subscriber))
        if (users contains userId) Behaviors.same
        else backend(users + (userId -> user), sessions)

      case AddAssistant(sessionId, position, replyTo) =>
        context.log.debug("[backend] AddAssistant({}, {})", sessionId, position)
        val assistantId = java.util.UUID.randomUUID.toString
        // join with virtual assistant user
        val joinResFut = context.self.ask[Either[SessionNotFound | SessionFull, Unit]](JoinLobby(assistantId, sessionId, _))
        // find position of assistant in lobby
        val infoFut = joinResFut flatMap {
          case Right(()) => context.self.ask[Either[SessionNotFound, Session.SessionInfo]](GetLobbyInfo(assistantId, sessionId, _))
          case Left(err) => Future.successful(Left(err))
        }
        // swap assistant with target position
        val swapFut = infoFut flatMap {
          case Right(info) =>
            info.users.find(_.id == assistantId).map(_.position) match
              case Some(currentAssistantPosition) => 
                context.self.ask[Either[SessionNotFound, Unit]](ForceSwap(sessionId, currentAssistantPosition, position, _))
              case None => Future.successful(Left(SessionNotFound))
          case Left(err) => Future.successful(Left(err))
        }
        // enable the assistant
        val setAssistantFut = swapFut flatMap {
          case Right(()) => context.self.ask[Either[UserNotInSession, Unit]](SetUserAsAssistant(assistantId, _))
          case Left(err) => Future.successful(Left(err))
        }
        // set assistant as ready
        val setReadyFut = setAssistantFut flatMap {
          case Right(()) => context.self.ask[Either[UserNotInSession, Unit]](SetUserReady(assistantId, true, _))
          case Left(err) => Future.successful(Left(err))
        }
        val replyFut = setReadyFut map {
          case Left(UserNotInSession) => Left(SessionNotFound)
          case Left(SessionNotFound) => Left(SessionNotFound)
          case Left(SessionFull) => Left(SessionFull)
          case Right(()) => Right(())
        }
        replyFut map (replyTo ! _)
        Behaviors.same

      case SetUserAsAssistant(userId, replyTo) =>
        context.log.debug("[backend] SetUserAsAssistant({})", userId)
        val user = users(userId)
        for
          session <- user.ask[Option[Session.Actor]](User.GetSession(_))
        do
          val res = session match {
            case Some(session) => session.ask[Unit](Session.SetUserAsAssistant(user, _)).map(Right.apply)
            case None => Future.successful(Left(UserNotInSession))
          }
          res map (replyTo ! _)
        if (users contains userId) Behaviors.same
        else backend(users + (userId -> user), sessions)
    }
  }
}
