package agh.bridge.back

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.actor.typed.scaladsl.AskPattern._

object Backend {
  type Actor = ActorRef[Command]

  import Session.SessionFull
  import User.UserNotInSession

  case object SessionNotFound
  type SessionNotFound = SessionNotFound.type

  sealed trait Command

  final case class Heartbeat(userId: User.Id) extends Command
  final case class FindSession(userId: User.Id, replyTo: ActorRef[Either[SessionNotFound, Session.Id]]) extends Command

  final case class CreateLobby(hostId: User.Id, replyTo: ActorRef[Session.Id]) extends Command
  final case class JoinLobby(sessionId: Session.Id, userId: User.Id, replyTo: ActorRef[Either[SessionNotFound | SessionFull, Unit]]) extends Command
  final case class LeaveLobby(userId: User.Id, replyTo: ActorRef[Unit]) extends Command
  final case class GetLobbyInfo(sessionId: Session.Id, replyTo: ActorRef[Either[SessionNotFound, Session.LobbyInfo]]) extends Command
  final case class SetUserReady(userId: User.Id, ready: Boolean, replyTo: ActorRef[Either[UserNotInSession, Unit]]) extends Command

  final case class HostJoinSessionResponse(
    sessionId: Session.Id,
    session: Session.Actor,
    createLobbyInitiator: ActorRef[Session.Id],
    result: Either[SessionFull, Unit]
  ) extends Command

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    context.setLoggerName("agh.bridge.back.Backend")
    val users: Map[User.Id, User.Actor] =
      Map.empty.withDefault(id => context.spawn(User(id), s"user-$id"))
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

      case JoinLobby(sessionId, userId, replyTo) =>
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

      case LeaveLobby(userId, replyTo) =>
        context.log.debug("[backend] LeaveLobby({})", userId)
        val user = users(userId)
        user ! User.LeaveSession(replyTo)
        if (users contains userId) Behaviors.same
        else backend(users + (userId -> user), sessions)

      case GetLobbyInfo(sessionId, replyTo) =>
        context.log.debug("[backend] GetLobbyInfo({})", sessionId)
        val sessionOpt = sessions.get(sessionId).toRight(SessionNotFound)
        val lobbyInfoOptFut = sessionOpt match {
          case Right(session) => session.ask[Session.LobbyInfo](Session.GetLobbyInfo(_)) map Right.apply
          case Left(_) => Future.successful(Left(SessionNotFound))
        }
        lobbyInfoOptFut map (replyTo ! _)
        Behaviors.same

      case SetUserReady(userId, ready, replyTo) =>
        context.log.debug("[backend] SetUserReady({}, {})", userId, ready)
        val user = users(userId)
        user ! User.SetReady(ready, replyTo)
        if (users contains userId) Behaviors.same
        else backend(users + (userId -> user), sessions)
    }
  }
}
