package agh.bridge.back

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.actor.typed.scaladsl.AskPattern._

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

object Backend {
  type Actor = ActorRef[Command]

  sealed trait Command

  final case class Heartbeat(userId: User.Id) extends Command
  final case class FindSession(userId: User.Id, replyTo: ActorRef[Option[Session.Id]]) extends Command

  final case class CreateLobby(hostId: User.Id, replyTo: ActorRef[Session.Id]) extends Command
  final case class JoinLobby(sessionId: Session.Id, userId: User.Id, replyTo: ActorRef[Either[Session.SessionFull.type, Unit]]) extends Command
  final case class LeaveLobby(userId: User.Id, replyTo: ActorRef[Either[Session.UserNotInSession.type, Unit]]) extends Command
  final case class GetLobbyInfo(sessionId: Session.Id, replyTo: ActorRef[Session.LobbyInfo]) extends Command
  final case class SetUserReady(userId: User.Id, ready: Boolean, replyTo: ActorRef[Unit]) extends Command

  def apply(users: Map[User.Id, User.Actor] = Map.empty,
            sessions: Map[Session.Id, Session.Actor] = Map.empty,
           ): Behavior[Command] = Behaviors.setup { context =>
    given ActorSystem[_] = context.system
    given ExecutionContext = context.executionContext
    given akka.util.Timeout = akka.util.Timeout(1000, java.util.concurrent.TimeUnit.MILLISECONDS)
    Behaviors.receiveMessage {

      case Heartbeat(userId) =>
        Behaviors.unhandled

      case FindSession(userId, replyTo) =>
        val userOptFut = Future.successful(users.get(userId))
        val sessionOptFut = userOptFut flatMap {
          case Some(user) => user.ask[Option[Session.Actor]](User.GetSession(_))
          case None => Future.successful(None)
        }
        val sessionIdOptFut = sessionOptFut flatMap {
          case Some(session) => session.ask[Session.Id](Session.GetId(_)) map Some.apply
          case None => Future.successful(None)
        }
        sessionIdOptFut.map (replyTo ! _)
        Behaviors.same

      case CreateLobby(hostId, replyTo) =>
        val host = context.spawn(User(hostId), s"user-$hostId")
        val sessionId = java.util.UUID.randomUUID.toString
        val session = context.spawn(Session(sessionId), s"session-$sessionId")
        val joinedFut = host.ask[Either[Session.SessionFull.type, Unit]](User.JoinSession(session, _))
        val x = joinedFut flatMap {
          case Right(_) => Future.successful(sessionId)
          case _ => Future.failed(new Exception("User should be able to join session they just created"))
        }
        x map (replyTo ! _)
        apply(
          users + (hostId -> host),
          sessions + (sessionId -> session),
        )

      case JoinLobby(sessionId, userId, replyTo) =>
        Behaviors.unhandled

      case LeaveLobby(userId, replyTo) =>
        Behaviors.unhandled

      case GetLobbyInfo(sessionId, replyTo) =>
        Behaviors.unhandled

      case SetUserReady(userId, ready, replyTo) =>
        Behaviors.unhandled
    }
  }
}
