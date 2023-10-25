package agh.bridge.back

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.StashBuffer
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }

object User {
  type Actor = ActorRef[Command]
  type Id = String

  import Session.SessionFull
  
  case object UserNotInSession
  type UserNotInSession = UserNotInSession.type

  sealed trait Command
  case object Heartbeat extends Command
  final case class JoinSession(session: Session.Actor, replyTo: ActorRef[Either[SessionFull, Unit]]) extends Command
  final case class LeaveSession(replyTo: ActorRef[Unit]) extends Command
  final case class SetReady(ready: Boolean, replyTo: ActorRef[Either[UserNotInSession, Unit]]) extends Command
  final case class GetReady(replyTo: ActorRef[Boolean]) extends Command
  final case class GetId(replyTo: ActorRef[Id]) extends Command
  final case class GetSession(replyTo: ActorRef[Option[Session.Actor]]) extends Command

  final case class SessionAddUserResponse(session: Session.Actor, result: Either[SessionFull, Unit]) extends Command

  given akka.util.Timeout = akka.util.Timeout(1000, java.util.concurrent.TimeUnit.MILLISECONDS)

  def apply(id: Id): Behavior[Command] = Behaviors.setup { context =>
    context.setLoggerName(s"agh.bridge.back.User-$id")
    unjoined(id)
  }

  private def unjoined(id: Id): Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {

        case Heartbeat =>
          context.log.debug("[unjoined] Heartbeat")
          Behaviors.same

        case JoinSession(session, replyTo) =>
          context.log.debug("[unjoined] JoinSession({})", session)
          val adapter = context.messageAdapter[Either[SessionFull, Unit]](SessionAddUserResponse(session, _))
          session ! Session.AddUser(context.self, adapter)
          joining(id, session, replyTo)

        case LeaveSession(replyTo) =>
          context.log.debug("[unjoined] LeaveSession")
          replyTo ! ()
          Behaviors.same

        case SetReady(ready, replyTo) =>
          context.log.debug("[unjoined] SetReady({})", ready)
          replyTo ! Left(UserNotInSession)
          Behaviors.same

        case GetReady(replyTo) =>
          context.log.debug("[unjoined] GetReady")
          replyTo ! false
          Behaviors.same

        case GetId(replyTo) =>
          context.log.debug("[unjoined] GetId")
          replyTo ! id
          Behaviors.same

        case GetSession(replyTo) =>
          context.log.debug("[unjoined] GetSession")
          replyTo ! None
          Behaviors.same

        case SessionAddUserResponse(session, r) =>
          context.log.error("[unjoined] SessionAddUserResponse({}, {})", session, r)
          Behaviors.unhandled
      }
    }

  private def joining(id: Id, targetSession: Session.Actor, initiator: ActorRef[Either[SessionFull, Unit]]): Behavior[Command] =
    Behaviors.withStash(32) { buffer =>
      Behaviors.setup { context =>
        Behaviors.receiveMessage {

          case Heartbeat =>
            context.log.debug("[joining] Heartbeat")
            Behaviors.same

          case JoinSession(session, replyTo) =>
            context.log.debug("[joining] JoinSession({}): stashing", session)
            buffer.stash(JoinSession(session, replyTo))
            Behaviors.same

          case LeaveSession(replyTo) =>
            context.log.debug("[joining] LeaveSession: stashing")
            buffer.stash(LeaveSession(replyTo))
            Behaviors.same

          case SetReady(ready, replyTo) =>
            context.log.debug("[joining] SetReady({}): stashing", ready)
            buffer.stash(SetReady(ready, replyTo))
            Behaviors.same

          case GetReady(replyTo) =>
            context.log.debug("[joining] GetReady")
            replyTo ! false
            Behaviors.same

          case GetId(replyTo) =>
            context.log.debug("[joining] GetId")
            replyTo ! id
            Behaviors.same

          case GetSession(replyTo) =>
            context.log.debug("[joining] GetSession")
            replyTo ! None
            Behaviors.same

          case SessionAddUserResponse(newSession, Right(())) if newSession == targetSession =>
            context.log.debug("[joining] SessionAddUserResponse: success")
            initiator ! Right(())
            buffer.unstashAll(joined(id, newSession))

          case SessionAddUserResponse(newSession, Left(SessionFull)) if newSession == targetSession =>
            context.log.debug("[joining] SessionAddUserResponse: session full")
            initiator ! Left(SessionFull)
            buffer.unstashAll(unjoined(id))

          case SessionAddUserResponse(_, _) =>
            context.log.error("[joining] SessionAddUserResponse: wrong session")
            Behaviors.unhandled
        }
      }
    }

  private def joined(id: Id, currentSession: Session.Actor, ready: Boolean = false): Behavior[Command] =
    Behaviors.setup { context =>
      given ActorSystem[_] = context.system
      given ExecutionContext = context.executionContext
      Behaviors.receiveMessage {

        case Heartbeat =>
          context.log.debug("[joined] Heartbeat")
          Behaviors.same

        case JoinSession(session, replyTo) =>
          context.log.debug("[joined] JoinSession({}): leaving old session", session)
          val removedFut = currentSession.ask[Unit](Session.RemoveUser(context.self, _))
          val adapter = context.messageAdapter[Either[SessionFull, Unit]](SessionAddUserResponse(session, _))
          removedFut.map(_ => session ! Session.AddUser(context.self, adapter))
          joining(id, session, replyTo)

        case LeaveSession(replyTo) =>
          context.log.debug("[joined] LeaveSession: leaving session")
          val removedFut = currentSession.ask[Unit](Session.RemoveUser(context.self, _))
          removedFut.map(_ => replyTo ! ())
          unjoined(id)

        case SetReady(ready, replyTo) =>
          context.log.debug("[joined] SetReady({})", ready)
          replyTo ! Right(())
          joined(id, currentSession, ready = ready)

        case GetReady(replyTo) =>
          context.log.debug("[joined] GetReady")
          replyTo ! ready
          Behaviors.same

        case GetId(replyTo) =>
          context.log.debug("[joined] GetId")
          replyTo ! id
          Behaviors.same

        case GetSession(replyTo) =>
          context.log.debug("[joined] GetSession")
          replyTo ! Some(currentSession)
          Behaviors.same

        case SessionAddUserResponse(_, _) =>
          context.log.error("[joined] SessionAddUserResponse")
          Behaviors.unhandled
      }
    }
}
