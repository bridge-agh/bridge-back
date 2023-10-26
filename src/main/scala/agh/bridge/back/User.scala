package agh.bridge.back

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.StashBuffer
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }

import agh.bridge.core.PlayerDirection
import Session.SessionFull

object User {
  type Actor = ActorRef[Command]
  type Id = String

  sealed trait Command
  
  case object Heartbeat extends Command
  
  final case class JoinSession(session: Session.Actor, replyTo: ActorRef[Either[SessionFull, Unit]]) extends Command
  final case class LeaveSession(replyTo: ActorRef[Unit]) extends Command
  
  final case class GetId(replyTo: ActorRef[Id]) extends Command
  final case class GetSession(replyTo: ActorRef[Option[Session.Actor]]) extends Command

  private final case class SessionAddUserResponse(session: Session.Actor, result: Either[SessionFull, Unit]) extends Command
  private case object SessionDied extends Command

  given akka.util.Timeout = akka.util.Timeout(1000, java.util.concurrent.TimeUnit.MILLISECONDS)

  def apply(id: Id): Behavior[Command] = unjoined(id)

  private def unjoined(id: Id): Behavior[Command] =
    Behaviors.setup { context =>
      context.setLoggerName(s"agh.bridge.back.User-$id [unjoined]")
      Behaviors.receiveMessage {

        case Heartbeat =>
          context.log.debug("Heartbeat")
          Behaviors.same

        case JoinSession(session, replyTo) =>
          context.log.debug("JoinSession")
          val adapter = context.messageAdapter[Either[SessionFull, Unit]](SessionAddUserResponse(session, _))
          session ! Session.AddUser(context.self, adapter)
          joining(id, session, replyTo)

        case LeaveSession(replyTo) =>
          context.log.warn("LeaveSession")
          replyTo ! ()
          Behaviors.same

        case GetId(replyTo) =>
          context.log.debug("GetId")
          replyTo ! id
          Behaviors.same

        case GetSession(replyTo) =>
          context.log.debug("GetSession")
          replyTo ! None
          Behaviors.same

        case SessionAddUserResponse(session, r) =>
          context.log.error("SessionAddUserResponse")
          Behaviors.unhandled

        case SessionDied =>
          context.log.error("SessionDied")
          Behaviors.unhandled
      }
    }

  private def joining(id: Id, targetSession: Session.Actor, initiator: ActorRef[Either[SessionFull, Unit]]): Behavior[Command] =
    Behaviors.withStash(32) { buffer =>
      Behaviors.setup { context =>
        context.setLoggerName(s"agh.bridge.back.User-$id [joining]")
        context.watchWith(targetSession, SessionDied)
        Behaviors.receiveMessage {

          case Heartbeat =>
            context.log.debug("Heartbeat")
            Behaviors.same

          case JoinSession(session, replyTo) =>
            context.log.debug("JoinSession: stashing")
            buffer.stash(JoinSession(session, replyTo))
            Behaviors.same

          case LeaveSession(replyTo) =>
            context.log.debug("LeaveSession: stashing")
            buffer.stash(LeaveSession(replyTo))
            Behaviors.same

          case GetId(replyTo) =>
            context.log.debug("GetId")
            replyTo ! id
            Behaviors.same

          case GetSession(replyTo) =>
            context.log.debug("GetSession")
            replyTo ! None
            Behaviors.same

          case SessionAddUserResponse(newSession, Right(())) if newSession == targetSession =>
            context.log.debug("SessionAddUserResponse: success")
            initiator ! Right(())
            buffer.unstashAll(joined(id, newSession))

          case SessionAddUserResponse(newSession, Left(SessionFull)) if newSession == targetSession =>
            context.log.debug("SessionAddUserResponse: session full")
            initiator ! Left(SessionFull)
            context.unwatch(targetSession)
            buffer.unstashAll(unjoined(id))

          case SessionAddUserResponse(_, _) =>
            context.log.error("SessionAddUserResponse: wrong session")
            Behaviors.unhandled

          case SessionDied =>
            context.log.error("SessionDied")
            buffer.unstashAll(unjoined(id))
        }
      }
    }

  private def joined(id: Id, currentSession: Session.Actor): Behavior[Command] =
    Behaviors.setup { context =>
      context.setLoggerName(s"agh.bridge.back.User-$id [joined]")
      given ActorSystem[_] = context.system
      given ExecutionContext = context.executionContext
      Behaviors.receiveMessage {

        case Heartbeat =>
          context.log.debug("Heartbeat")
          Behaviors.same

        case JoinSession(session, replyTo) =>
          context.log.debug("JoinSession: leaving old session")
          val removedFut = currentSession.ask[Unit](Session.RemoveUser(context.self, _))
          val adapter = context.messageAdapter[Either[SessionFull, Unit]](SessionAddUserResponse(session, _))
          removedFut.map(_ => session ! Session.AddUser(context.self, adapter))
          context.unwatch(currentSession)
          joining(id, session, replyTo)

        case LeaveSession(replyTo) =>
          context.log.debug("LeaveSession: leaving session")
          val removedFut = currentSession.ask[Unit](Session.RemoveUser(context.self, _))
          removedFut.map(_ => replyTo ! ())
          context.unwatch(currentSession)
          unjoined(id)

        case GetId(replyTo) =>
          context.log.debug("GetId")
          replyTo ! id
          Behaviors.same

        case GetSession(replyTo) =>
          context.log.debug("GetSession")
          replyTo ! Some(currentSession)
          Behaviors.same

        case SessionAddUserResponse(_, _) =>
          context.log.error("SessionAddUserResponse")
          Behaviors.unhandled

        case SessionDied =>
          context.log.error("SessionDied")
          unjoined(id)
      }
    }
}
