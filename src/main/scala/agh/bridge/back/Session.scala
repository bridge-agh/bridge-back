package agh.bridge.back

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.actor.typed.scaladsl.AskPattern._

object Session {
  type Actor = ActorRef[Command]
  type Id = String

  case object SessionFull
  type SessionFull = SessionFull.type

  sealed trait Command
  final case class AddUser(user: User.Actor, replyTo: ActorRef[Either[SessionFull, Unit]]) extends Command
  final case class RemoveUser(user: User.Actor, replyTo: ActorRef[Unit]) extends Command
  final case class GetLobbyInfo(replyTo: ActorRef[LobbyInfo]) extends Command
  final case class GetId(replyTo: ActorRef[Id]) extends Command

  final case class Player(id: User.Id, ready: Boolean, position: Int)
  final case class LobbyInfo(host: User.Id, users: List[Player], started: Boolean)

  private final case class UserDied(user: User.Actor) extends Command

  def apply(id: Id): Behavior[Command] = session(id, List.empty)

  private def session(id: Id, users: List[User.Actor]): Behavior[Command] =
    Behaviors.setup { context =>
      given ActorSystem[_] = context.system
      given ExecutionContext = context.executionContext
      given akka.util.Timeout = akka.util.Timeout(1000, java.util.concurrent.TimeUnit.MILLISECONDS)
      context.setLoggerName(s"agh.bridge.back.Session-$id")
      Behaviors.receiveMessage {

        case AddUser(user, replyTo) if users.length < 4 =>
          context.log.debug("[session] AddUser")
          replyTo ! Right(())
          context.watchWith(user, UserDied(user))
          session(id, users :+ user)
          
        case AddUser(user, replyTo) =>
          context.log.debug("[session] AddUser - session full")
          replyTo ! Left(SessionFull)
          Behaviors.same

        case RemoveUser(user, replyTo) if users.contains(user) && users.length > 1 =>
          context.log.debug("[session] RemoveUser")
          replyTo ! ()
          context.unwatch(user)
          session(id, users filterNot(_ == user))

        case RemoveUser(user, replyTo) if users.contains(user) =>
          context.log.debug("[session] RemoveUser - last user")
          replyTo ! ()
          context.unwatch(user)
          Behaviors.stopped

        case RemoveUser(user, replyTo) =>
          context.log.warn("[session] RemoveUser - user not in session")
          replyTo ! ()
          Behaviors.same

        case GetLobbyInfo(replyTo) =>
          context.log.debug("[session] GetLobbyInfo")
          val started = false
          val host = users.head
          val hostIdFut = host.ask[User.Id](User.GetId(_))
          val idsFut = Future.sequence(users map { user =>
            user.ask[User.Id](User.GetId(_))
          })
          val readyFut =  Future.sequence(users map { user =>
            user.ask[Boolean](User.GetReady(_))
          })
          val playersFut = for {
            ids <- idsFut
            ready <- readyFut
          } yield ids.zip(ready).zipWithIndex.map { case ((id, ready), position) =>
            Player(id, ready, position)
          }
          val info = for {
            hostId <- hostIdFut
            players <- playersFut
          } yield LobbyInfo(hostId, players, started)
          info map (replyTo ! _)
          Behaviors.same

        case GetId(replyTo) =>
          context.log.debug("[session] GetId")
          replyTo ! id
          Behaviors.same

        case UserDied(user) if users.contains(user) && users.length > 1 =>
          context.log.error("[session] UserDied")
          session(id, users filterNot(_ == user))

        case UserDied(user) if users.contains(user) =>
          context.log.error("[session] UserDied - last user")
          Behaviors.stopped

        case UserDied(user) =>
          context.log.error("[session] UserDied - user not in session")
          Behaviors.unhandled
      }
    }
}
