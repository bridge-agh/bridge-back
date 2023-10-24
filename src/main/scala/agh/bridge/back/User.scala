package agh.bridge.back

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.actor.typed.scaladsl.AskPattern._

object User {
  type Actor = ActorRef[Command]
  type Id = String

  sealed trait Command
  final case class JoinSession(session: Session.Actor, replyTo: ActorRef[Either[JoinSessionError, Unit]]) extends Command
  final case class LeaveSession(replyTo: ActorRef[Unit]) extends Command
  final case class SetReady(ready: Boolean, replyTo: ActorRef[Either[SetReadyError, Unit]]) extends Command
  final case class GetReady(replyTo: ActorRef[Boolean]) extends Command
  final case class GetId(replyTo: ActorRef[Id]) extends Command
  final case class GetSession(replyTo: ActorRef[Option[Session.Actor]]) extends Command

  final case class SessionAddUserResponseWrapper(result: Either[Session.AddUserError, Unit]) extends Command

  case object UserAlreadyInSession
  case object UserNotInSession
  import Session.SessionFull
  import Session.AddUserError

  type JoinSessionError = SessionFull.type | UserAlreadyInSession.type
  type SetReadyError = UserNotInSession.type

  def apply(id: Id) = unjoined(id)

  private def unjoined(id: Id): Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {

        case JoinSession(session, replyTo) =>
          val adapter = context.messageAdapter[Either[AddUserError, Unit]](SessionAddUserResponseWrapper.apply)
          session ! Session.AddUser(context.self, adapter)
          joining(id, session, replyTo)

        case LeaveSession(replyTo) =>
          replyTo ! ()
          Behaviors.same

        case SetReady(_, replyTo) =>
          replyTo ! Left(UserNotInSession)
          Behaviors.same

        case GetReady(replyTo) =>
          replyTo ! false
          Behaviors.same

        case GetId(replyTo) =>
          replyTo ! id
          Behaviors.same

        case GetSession(replyTo) =>
          replyTo ! None
          Behaviors.same

        case SessionAddUserResponseWrapper(_) =>
          Behaviors.unhandled
      }
    }

  private def joining(id: Id, session: Session.Actor, replyTo: ActorRef[Either[JoinSessionError, Unit]]): Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {

        case JoinSession(session, replyTo) =>
          replyTo ! Left(UserAlreadyInSession)
          Behaviors.same

        case LeaveSession(replyTo) =>
          session ! Session.RemoveUser(context.self)
          replyTo ! ()
          Behaviors.same

        case SetReady(_, replyTo) =>
          replyTo ! Left(UserNotInSession)
          Behaviors.same

        case GetReady(replyTo) =>
          replyTo ! false
          Behaviors.same

        case GetId(replyTo) =>
          replyTo ! id
          Behaviors.same

        case GetSession(replyTo) =>
          replyTo ! None
          Behaviors.same

        case SessionAddUserResponseWrapper(result) =>
          replyTo ! result
          result match {
            case Right(_) => joined(id, session)
            case _ => unjoined(id)
          }
      }
    }

  private def joined(id: Id, session: Session.Actor, ready: Boolean = false): Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {

        case JoinSession(session, replyTo) =>
          replyTo ! Left(UserAlreadyInSession)
          Behaviors.same

        case LeaveSession(replyTo) =>
          session ! Session.RemoveUser(context.self)
          unjoined(id)

        case SetReady(_, replyTo) =>
          replyTo ! Right(())
          joined(id, session, ready = true)

        case GetReady(replyTo) =>
          replyTo ! ready
          Behaviors.same

        case GetId(replyTo) =>
          replyTo ! id
          Behaviors.same

        case GetSession(replyTo) =>
          replyTo ! Some(session)
          Behaviors.same

        case SessionAddUserResponseWrapper(_) =>
          Behaviors.unhandled
      }
    }
}
