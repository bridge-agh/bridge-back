package agh.bridge.back

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.actor.typed.scaladsl.AskPattern._

object Backend {
  type Actor = ActorRef[Command]

  import User.SetReadyError
  import User.JoinSessionError

  case object SessionNotFound
  type JoinLobbyError = JoinSessionError | SessionNotFound.type

  sealed trait Command

  final case class Heartbeat(userId: User.Id) extends Command
  final case class FindSession(userId: User.Id, replyTo: ActorRef[Option[Session.Id]]) extends Command

  final case class CreateLobby(hostId: User.Id, replyTo: ActorRef[Session.Id]) extends Command
  final case class JoinLobby(sessionId: Session.Id, userId: User.Id, replyTo: ActorRef[Either[JoinLobbyError, Unit]]) extends Command
  final case class LeaveLobby(userId: User.Id) extends Command
  final case class GetLobbyInfo(sessionId: Session.Id, replyTo: ActorRef[Option[Session.LobbyInfo]]) extends Command
  final case class SetUserReady(userId: User.Id, ready: Boolean, replyTo: ActorRef[Either[SetReadyError, Unit]]) extends Command

  final case class HostJoinSessionResponse(
    sessionId: Session.Id,
    session: Session.Actor,
    replyTo: ActorRef[Session.Id],
    result: Either[JoinSessionError, Unit]
  ) extends Command

  def apply(): Behavior[Command] = Behaviors.setup { context =>
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
        val user = users(userId)
        user ! User.Heartbeat
        if (users contains userId) Behaviors.same
        else backend(users + (userId -> user), sessions)

      case FindSession(userId, replyTo) =>
        val user = users(userId)
        val sessionOptFut = user.ask[Option[Session.Actor]](User.GetSession(_))
        val sessionIdOptFut = sessionOptFut flatMap {
          case Some(session) => session.ask[Session.Id](Session.GetId(_)) map Some.apply
          case None => Future.successful(None)
        }
        sessionIdOptFut.map (replyTo ! _)
        if (users contains userId) Behaviors.same
        else backend(users + (userId -> user), sessions)

      case CreateLobby(hostId, replyTo) =>
        val host = users(hostId)
        val sessionId = java.util.UUID.randomUUID.toString
        val session = context.spawn(Session(sessionId), s"session-$sessionId")
        val adapter = context.messageAdapter[Either[JoinSessionError, Unit]](
          HostJoinSessionResponse(sessionId, session, replyTo, _))
        host ! User.JoinSession(session, adapter)
        if (users contains hostId) Behaviors.same
        else backend(users + (hostId -> host), sessions)

      case HostJoinSessionResponse(sessionId, session, replyTo, Right(_)) =>
        replyTo ! sessionId
        backend(users, sessions + (sessionId -> session))

      case HostJoinSessionResponse(_, _, _, Left(_)) =>
        Behaviors.same

      case JoinLobby(sessionId, userId, replyTo) =>
        val user = users(userId)
        val sessionOpt = sessions.get(sessionId)
        val joinResult = Future.successful(sessionOpt) flatMap {
          case Some(session) => user.ask[Either[JoinSessionError, Unit]](User.JoinSession(session, _))
          case None => Future.successful(Left(SessionNotFound))
        }
        joinResult map (replyTo ! _)
        if (users contains userId) Behaviors.same
        else backend(users + (userId -> user), sessions)

      case LeaveLobby(userId) =>
        val user = users(userId)
        user ! User.LeaveSession
        if (users contains userId) Behaviors.same
        else backend(users + (userId -> user), sessions)

      case GetLobbyInfo(sessionId, replyTo) =>
        val sessionOpt = sessions.get(sessionId)
        val lobbyInfoOptFut = Future.successful(sessionOpt) flatMap {
          case Some(session) => session.ask[Session.LobbyInfo](Session.GetLobbyInfo(_)) map Some.apply
          case None => Future.successful(None)
        }
        lobbyInfoOptFut map (replyTo ! _)
        Behaviors.same

      case SetUserReady(userId, ready, replyTo) =>
        val user = users(userId)
        user ! User.SetReady(ready, replyTo)
        if (users contains userId) Behaviors.same
        else backend(users + (userId -> user), sessions)
    }
  }
}
