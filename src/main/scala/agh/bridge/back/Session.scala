package agh.bridge.back

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.actor.typed.scaladsl.AskPattern._

import agh.bridge.core.PlayerDirection

object Session {
  type Actor = ActorRef[Command]
  type Id = String

  private final case class LobbyUserState(
    ready: Boolean,
    position: PlayerDirection,
  )

  private final case class LobbyState(
    host: User.Actor,
    users: Map[User.Actor, LobbyUserState],
) {
    def addUser(user: User.Actor) = {
      val direction = (PlayerDirection.values diff users.values.map(_.position).toSeq).head
      LobbyState(host, users + (user -> LobbyUserState(false, direction)))
    }

    def removeUser(user: User.Actor) = {
      val newUsers = users - user
      val newHost = if (user == host) newUsers.keys.head else host
      LobbyState(newHost, newUsers)
    }

    def setUserReady(user: User.Actor, ready: Boolean) = {
      LobbyState(host, users.updated(user, LobbyUserState(ready, users(user).position)))
    }

    def forceSwap(first: PlayerDirection, second: PlayerDirection) = {
      LobbyState(
        host,
        users.map { case (user, state) =>
          val newPosition = if (state.position == first) second else if (state.position == second) first else state.position
          user -> LobbyUserState(state.ready, newPosition)
        }
      )
    }

    def allReady = users.size == 4 && users.values.forall(_.ready)
}

  private object LobbyState {
    def apply(host: User.Actor): LobbyState = LobbyState(host, Map(host -> LobbyUserState(false, PlayerDirection.North)))
  }

  private final case class GameState()

  case object SessionFull
  type SessionFull = SessionFull.type

  case object UserNotInSession
  type UserNotInSession = UserNotInSession.type

  sealed trait Command
  final case class AddUser(user: User.Actor, replyTo: ActorRef[Either[SessionFull, Unit]]) extends Command
  final case class RemoveUser(user: User.Actor, replyTo: ActorRef[Unit]) extends Command
  final case class SetUserReady(user: User.Actor, ready: Boolean, replyTo: ActorRef[Either[UserNotInSession, Unit]]) extends Command
  final case class ForceSwap(first: PlayerDirection, second: PlayerDirection, replyTo: ActorRef[Unit]) extends Command
  final case class GetLobbyInfo(replyTo: ActorRef[LobbyInfo]) extends Command
  final case class GetId(replyTo: ActorRef[Id]) extends Command

  final case class Player(id: User.Id, ready: Boolean, position: PlayerDirection)
  final case class LobbyInfo(host: User.Id, users: List[Player], started: Boolean)

  private final case class UserDied(user: User.Actor) extends Command

  given akka.util.Timeout = akka.util.Timeout(1000, java.util.concurrent.TimeUnit.MILLISECONDS)

  def apply(id: Id): Behavior[Command] = empty(id)

  private def empty(id: Id): Behavior[Command] =
    Behaviors.setup { context =>
      given ActorSystem[_] = context.system
      given ExecutionContext = context.executionContext
      context.setLoggerName(s"agh.bridge.back.Session-$id [empty]")
      Behaviors.receiveMessage {

        case AddUser(user, replyTo) =>
          context.log.debug("AddUser: first user")
          context.watchWith(user, UserDied(user))
          replyTo ! Right(())
          lobby(id, LobbyState(user, Map(user -> LobbyUserState(false, PlayerDirection.North))))

        case RemoveUser(user, replyTo) =>
          context.log.error("RemoveUser: no users")
          Behaviors.unhandled

        case SetUserReady(user, ready, replyTo) => 
          context.log.error("SetUserReady: no users")
          Behaviors.unhandled

        case ForceSwap(first, second, replyTo) =>
          context.log.error("ForceSwap: no users")
          Behaviors.unhandled

        case GetLobbyInfo(replyTo) =>
          context.log.error("GetLobbyInfo: no users")
          Behaviors.unhandled

        case GetId(replyTo) =>
          context.log.debug("GetId")
          replyTo ! id
          Behaviors.same

        case UserDied(user) =>
          context.log.error("UserDied - no users")
          Behaviors.unhandled
      }
    }

  private def lobby(id: Id, state: LobbyState): Behavior[Command] =
    Behaviors.setup { context =>
      given ActorSystem[_] = context.system
      given ExecutionContext = context.executionContext
      context.setLoggerName(s"agh.bridge.back.Session-$id [lobby]")
      context.watchWith(state.host, UserDied(state.host))
      Behaviors.receiveMessage {

        case AddUser(user, replyTo) if state.users.size < 4 =>
          context.log.debug("AddUser")
          context.watchWith(user, UserDied(user))
          replyTo ! Right(())
          lobby(id, state.addUser(user))

        case AddUser(user, replyTo) =>
          context.log.debug("AddUser - session full")
          replyTo ! Left(SessionFull)
          Behaviors.same

        case RemoveUser(user, replyTo) if state.users.contains(user) && state.users.size > 1 =>
          context.log.debug("RemoveUser")
          replyTo ! ()
          context.unwatch(user)
          lobby(id, state.removeUser(user))

        case RemoveUser(user, replyTo) if state.users.contains(user) =>
          context.log.debug("RemoveUser - last user")
          replyTo ! ()
          Behaviors.stopped

        case RemoveUser(user, replyTo) =>
          context.log.error("RemoveUser - user not in session")
          Behaviors.unhandled

        case SetUserReady(user, ready, replyTo) if state.users.contains(user) =>
          context.log.debug("SetUserReady")
          replyTo ! Right(())
          lobby(id, state.setUserReady(user, ready))

        case SetUserReady(user, ready, replyTo) =>
          context.log.error("SetUserReady - user not in session")
          replyTo ! Left(UserNotInSession)
          Behaviors.same

        case ForceSwap(first, second, replyTo) =>
          context.log.debug("ForceSwap")
          replyTo ! ()
          lobby(id, state.forceSwap(first, second))

        case GetLobbyInfo(replyTo) =>
          context.log.debug("GetLobbyInfo")
          val host = state.host
          val ready = state.users.values.map(_.ready)
          val position = state.users.values.map(_.position)
          for
            hostId <- host.ask[User.Id](User.GetId(_))
            playerIds <- Future.sequence(state.users.keys map { user =>
                           user.ask[User.Id](User.GetId(_))
                         })
          do
            val info = LobbyInfo(
              hostId,
              (playerIds zip ready zip position).map { case ((id, ready), position) =>
                Player(id, ready, position)
              }.toList,
              state.allReady,
            )
            replyTo ! info
          Behaviors.same

        case GetId(replyTo) =>
          context.log.debug("GetId")
          replyTo ! id
          Behaviors.same

        case UserDied(user) if state.users.contains(user) && state.users.nonEmpty =>
          context.log.error("UserDied")
          lobby(id, state.removeUser(user))

        case UserDied(user) if state.users.contains(user) =>
          context.log.error("UserDied - last user")
          Behaviors.stopped

        case UserDied(user) =>
          context.log.error("UserDied - user not in session")
          Behaviors.unhandled
      }
    }
}
