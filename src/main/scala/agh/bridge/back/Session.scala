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

  private final case class SessionUserState private(ready: Boolean, position: PlayerDirection):
    def setReady(ready: Boolean) = SessionUserState(ready, position)

    def setPosition(position: PlayerDirection) = SessionUserState(ready, position)

  private object SessionUserState:
    def apply(position: PlayerDirection): SessionUserState = SessionUserState(false, position)

  private final case class SessionState private(host: User.Actor, users: Map[User.Actor, SessionUserState]):
    def addUser(user: User.Actor) =
      val direction = (PlayerDirection.values diff users.values.map(_.position).toSeq).head
      SessionState(host, users + (user -> SessionUserState(direction)))

    def removeUser(user: User.Actor) =
      val newUsers = users - user
      val newHost = if (user == host) newUsers.keys.head else host
      SessionState(newHost, newUsers)

    def setReady(user: User.Actor, ready: Boolean) =
      SessionState(host, users.updated(user, users(user).setReady(ready)))

    def forceSwap(first: PlayerDirection, second: PlayerDirection) =
      SessionState(
        host,
        users.mapValues { user =>
          val newPosition = user.position match
            case `first` => second
            case `second` => first
            case other => other
          user.setPosition(newPosition)
        }.toMap
      )

    def allReady = users.size == 4 && users.values.forall(_.ready)

  private object SessionState {
    def apply(host: User.Actor): SessionState = SessionState(host, Map(host -> SessionUserState(PlayerDirection.North)))
  }

  private final case class GameState private(session: SessionState)

  private object GameState:
    def apply(sessionState: SessionState): GameState = new GameState(sessionState)

  case object SessionFull
  type SessionFull = SessionFull.type

  final case class SessionInfo(host: User.Id, users: List[PlayerInfo], started: Boolean)
  final case class PlayerInfo(id: User.Id, ready: Boolean, position: PlayerDirection)

  sealed trait Command
  final case class GetId(replyTo: ActorRef[Id]) extends Command
  final case class RemoveUser(user: User.Actor, replyTo: ActorRef[Unit]) extends Command
  final case class GetInfo(replyTo: ActorRef[SessionInfo]) extends Command
  final case class AddSubscriber(replyTo: ActorRef[SessionInfo]) extends Command

  sealed trait LobbyCommand extends Command
  final case class AddUser(user: User.Actor, replyTo: ActorRef[Either[SessionFull, Unit]]) extends LobbyCommand
  final case class SetUserReady(user: User.Actor, ready: Boolean, replyTo: ActorRef[Unit]) extends LobbyCommand
  final case class ForceSwap(first: PlayerDirection, second: PlayerDirection, replyTo: ActorRef[Unit]) extends LobbyCommand

  sealed trait GameCommand extends Command

  private final case class UserDied(user: User.Actor) extends Command
  private final case class SubscriberDied(subscriber: ActorRef[SessionInfo]) extends Command

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
          val state = SessionState(user)
          context.watchWith(user, UserDied(user))
          replyTo ! Right(())
          lobby(id, state, Nil)

        case RemoveUser(user, replyTo) =>
          context.log.error("RemoveUser: no users")
          Behaviors.unhandled

        case SetUserReady(user, ready, replyTo) =>
          context.log.error("SetUserReady: no users")
          Behaviors.unhandled

        case ForceSwap(first, second, replyTo) =>
          context.log.error("ForceSwap: no users")
          Behaviors.unhandled

        case GetInfo(replyTo) =>
          context.log.error("GetInfo: empty session")
          Behaviors.unhandled

        case GetId(replyTo) =>
          context.log.debug("GetId")
          replyTo ! id
          Behaviors.same

        case UserDied(user) =>
          context.log.error("UserDied - no users")
          Behaviors.unhandled

        case AddSubscriber(replyTo) =>
          context.log.error("AddSubscriber: empty session")
          Behaviors.unhandled

        case SubscriberDied(subscriber) =>
          context.log.error("SubscriberDied: empty session")
          Behaviors.unhandled
      }
    }

  private def lobby(id: Id, state: SessionState, subs: List[ActorRef[SessionInfo]]): Behavior[Command] =
    Behaviors.setup { context =>
      given ActorSystem[_] = context.system
      given ExecutionContext = context.executionContext
      context.setLoggerName(s"agh.bridge.back.Session-$id [lobby]")
      context.watchWith(state.host, UserDied(state.host))
      Behaviors.receiveMessage {

        case AddUser(user, replyTo) if state.users.size < 4 =>
          context.log.debug("AddUser")
          val newState = state.addUser(user)
          context.watchWith(user, UserDied(user))
          replyTo ! Right(())
          notifySubscribers(subs, newState)
          lobby(id, newState, subs)

        case AddUser(user, replyTo) =>
          context.log.debug("AddUser - session full")
          replyTo ! Left(SessionFull)
          Behaviors.same

        case RemoveUser(user, replyTo) if state.users.contains(user) && state.users.size > 1 =>
          context.log.debug("RemoveUser")
          val newState = state.removeUser(user)
          replyTo ! ()
          context.unwatch(user)
          notifySubscribers(subs, newState)
          lobby(id, newState, subs)

        case RemoveUser(user, replyTo) if state.users.contains(user) =>
          context.log.debug("RemoveUser - last user")
          replyTo ! ()
          Behaviors.stopped

        case RemoveUser(user, replyTo) =>
          context.log.error("RemoveUser - user not in session")
          Behaviors.unhandled

        case SetUserReady(user, ready, replyTo) if state.users.contains(user) =>
          context.log.debug("SetUserReady")
          val newState = state.setReady(user, ready)
          replyTo ! Right(())
          notifySubscribers(subs, newState)
          if newState.allReady then
            context.log.debug("SetUserReady - starting game")
            game(id, GameState(newState), subs)
          else
            lobby(id, newState, subs)

        case SetUserReady(user, ready, replyTo) =>
          context.log.error("SetUserReady - user not in session")
          Behaviors.unhandled

        case ForceSwap(first, second, replyTo) =>
          context.log.debug("ForceSwap")
          val newState = state.forceSwap(first, second)
          replyTo ! ()
          notifySubscribers(subs, newState)
          lobby(id, newState, subs)

        case GetInfo(replyTo) =>
          context.log.debug("GetInfo")
          sendInfo(replyTo, state)
          Behaviors.same

        case GetId(replyTo) =>
          context.log.debug("GetId")
          replyTo ! id
          Behaviors.same

        case UserDied(user) if state.users.contains(user) && state.users.nonEmpty =>
          context.log.error("UserDied")
          val newState = state.removeUser(user)
          notifySubscribers(subs, newState)
          lobby(id, newState, subs)

        case UserDied(user) if state.users.contains(user) =>
          context.log.error("UserDied - last user")
          Behaviors.stopped

        case UserDied(user) =>
          context.log.error("UserDied - user not in session")
          Behaviors.unhandled

        case AddSubscriber(replyTo) =>
          context.log.debug("AddSubscriber")
          context.watchWith(replyTo, SubscriberDied(replyTo))
          sendInfo(replyTo, state)
          lobby(id, state, replyTo :: subs)

        case SubscriberDied(subscriber) =>
          context.log.debug("SubscriberDied")
          lobby(id, state, subs.filterNot(_ == subscriber))
      }
    }

  private def game(id: Id, state: GameState, subs: List[ActorRef[SessionInfo]]): Behavior[Command] =
    Behaviors.setup { context =>
      given ActorSystem[_] = context.system
      given ExecutionContext = context.executionContext
      context.setLoggerName(s"agh.bridge.back.Session-$id [game]")
      Behaviors.receiveMessage {

        case AddUser(user, replyTo) =>
          context.log.error("AddUser - game already started")
          Behaviors.unhandled

        case RemoveUser(user, replyTo) =>
          context.log.debug("RemoveUser - killing game")
          throw NotImplementedError()

        case SetUserReady(user, ready, replyTo) =>
          context.log.error("SetUserReady - game already started")
          Behaviors.unhandled

        case ForceSwap(first, second, replyTo) =>
          context.log.error("ForceSwap - game already started")
          Behaviors.unhandled

        case GetInfo(replyTo) =>
          context.log.debug("GetInfo")
          sendInfo(replyTo, state.session)
          Behaviors.same

        case GetId(replyTo) =>
          context.log.debug("GetId")
          replyTo ! id
          Behaviors.same

        case UserDied(user) =>
          context.log.error("UserDied - killing game")
          throw NotImplementedError()

        case AddSubscriber(replyTo) =>
          context.log.debug("AddSubscriber")
          context.watchWith(replyTo, SubscriberDied(replyTo))
          sendInfo(replyTo, state.session)
          game(id, state, replyTo :: subs)

        case SubscriberDied(subscriber) =>
          context.log.debug("SubscriberDied")
          game(id, state, subs.filterNot(_ == subscriber))
      }
    }

  private def sendInfo(replyTo: ActorRef[SessionInfo], state: SessionState)(using ActorSystem[_], ExecutionContext): Unit =
    val host = state.host
    val ready = state.users.values.map(_.ready)
    val position = state.users.values.map(_.position)
    for
      hostId <- host.ask[User.Id](User.GetId(_))
      playerIds <- Future.sequence(state.users.keys.map { user =>
                     user.ask[User.Id](User.GetId(_))
                   })
    do
      val info = SessionInfo(
        hostId,
        (playerIds zip ready zip position).map { case ((id, ready), position) =>
          PlayerInfo(id, ready, position)
        }.toList,
        state.allReady,
      )
      replyTo ! info

  private def notifySubscribers(subs: List[ActorRef[SessionInfo]], state: SessionState)(using ActorSystem[_], ExecutionContext): Unit =
    subs.foreach(sendInfo(_, state))
}
