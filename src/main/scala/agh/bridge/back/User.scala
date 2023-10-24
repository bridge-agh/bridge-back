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

object User {
  type Actor = ActorRef[Command]
  type Id = String

  sealed trait Command
  final case class JoinSession(session: Session.Actor, replyTo: ActorRef[Either[Session.SessionFull.type, Unit]]) extends Command
  final case class LeaveSession(replyTo: ActorRef[Either[Session.UserNotInSession.type, Unit]]) extends Command
  final case class SetReady(ready: Boolean, replyTo: ActorRef[Either[Session.UserNotInSession.type, Unit]]) extends Command
  final case class GetReady(replyTo: ActorRef[Either[Session.UserNotInSession.type, Boolean]]) extends Command
  final case class GetId(replyTo: ActorRef[Id]) extends Command
  final case class GetSession(replyTo: ActorRef[Option[Session.Actor]]) extends Command

  final case class JoinSessionResult(result: Either[Session.SessionFull.type, Unit]) extends Command

  def apply(id: Id) = unjoined(id)

  private def unjoined(id: Id): Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {

        case JoinSession(session, replyTo) =>
          val adapter = context.messageAdapter[Either[Session.SessionFull.type, Unit]](JoinSessionResult.apply)
          session ! Session.AddUser(context.self, adapter)
          joining(id, session, replyTo)

        case LeaveSession(replyTo) =>
          replyTo ! Left(Session.UserNotInSession)
          Behaviors.same

        case SetReady(_, replyTo) =>
          replyTo ! Left(Session.UserNotInSession)
          Behaviors.same

        case GetReady(replyTo) =>
          replyTo ! Left(Session.UserNotInSession)
          Behaviors.same

        case GetId(replyTo) =>
          replyTo ! id
          Behaviors.same

        case GetSession(replyTo) =>
          replyTo ! None
          Behaviors.same

        case JoinSessionResult(_) =>
          Behaviors.unhandled
      }
    }

  private def joining(id: Id, session: Session.Actor, replyTo: ActorRef[Either[Session.SessionFull.type, Unit]]): Behavior[Command] =
    Behaviors.receiveMessage {
      case JoinSessionResult(result) =>
        replyTo ! result
        result match {
          case Right(_) => joined(id, session)
          case _ => unjoined(id)
        }

      case _ => Behaviors.unhandled
    }

  private def joined(id: Id, session: Session.Actor, ready: Boolean = false): Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case JoinSession(session, replyTo) =>
          Behaviors.unhandled

        case LeaveSession(replyTo) =>
          session ! Session.RemoveUser(context.self, replyTo)
          unjoined(id)

        case SetReady(_, replyTo) =>
          replyTo ! Right(())
          joined(id, session, ready = true)

        case GetReady(replyTo) =>
          replyTo ! Right(ready)
          Behaviors.same

        case GetId(replyTo) =>
          replyTo ! id
          Behaviors.same

        case GetSession(replyTo) =>
          replyTo ! Some(session)
          Behaviors.same

        case JoinSessionResult(_) =>
          Behaviors.unhandled
      }
    }
}
