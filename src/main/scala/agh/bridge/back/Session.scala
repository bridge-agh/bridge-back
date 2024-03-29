package agh.bridge.back

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.actor.typed.scaladsl.AskPattern._

import agh.bridge.core.PlayerDirection
import agh.bridge.core as Core

object Session {
  type Actor = ActorRef[Command]
  type Id = String

  private final case class LobbyUserState private(ready: Boolean, position: PlayerDirection, isHuman: Boolean):
    def setReady(ready: Boolean) = LobbyUserState(ready, position, isHuman)

    def setPosition(position: PlayerDirection) = LobbyUserState(ready, position, isHuman)

    def setAssistant() = copy(isHuman = false)

  private object LobbyUserState:
    def apply(position: PlayerDirection, isHuman: Boolean): LobbyUserState = LobbyUserState(false, position, isHuman)

  private final case class LobbyState private(host: User.Actor, users: Map[User.Actor, LobbyUserState], assistantLevel: Int):
    def addUser(user: User.Actor) =
      val direction = (PlayerDirection.values diff users.values.map(_.position).toSeq).head
      LobbyState(host, users + (user -> LobbyUserState(direction, true)), assistantLevel)

    def removeUser(user: User.Actor) =
      val newUsers = users - user
      val newHost = if (user == host) newUsers.keys.head else host
      LobbyState(newHost, newUsers, assistantLevel)

    def setReady(user: User.Actor, ready: Boolean) =
      LobbyState(host, users.updated(user, users(user).setReady(ready)), assistantLevel)

    def promoteUserToHost(user: User.Actor) =
      LobbyState(user, users, assistantLevel)

    def forceSwap(first: PlayerDirection, second: PlayerDirection) =
      LobbyState(
        host,
        users.mapValues { user =>
          val newPosition = user.position match
            case `first` => second
            case `second` => first
            case other => other
          user.setPosition(newPosition)
        }.toMap,
        assistantLevel,
      )

    def setUserAsAssistant(user: User.Actor) =
      val newUsers = users.map {
        case (u, state) if u == user => (u, state.setAssistant())
        case o => o
      }
      copy(users = newUsers)

    def allReady = users.size == 4 && users.values.forall(_.ready)

    def setAssistantLevel(level: Int) = LobbyState(host, users, math.max(1, math.min(level, 5)))

  private object LobbyState {
    def apply(host: User.Actor): LobbyState = LobbyState(host, Map(host -> LobbyUserState(PlayerDirection.North, true)), 1)
  }

  private final case class GameState private(
    impl: Core.Game,
  )

  private object GameState:
    // Seed is set to 0 for development purposes
    def apply(): GameState = GameState(Core.Game(0))

  private final case class SessionState private(lobby: LobbyState, game: Option[GameState]):
    def addUser(user: User.Actor) = SessionState(lobby.addUser(user), game)

    def removeUser(user: User.Actor) = SessionState(lobby.removeUser(user), game)

    def setReady(user: User.Actor, ready: Boolean) = SessionState(lobby.setReady(user, ready), game)

    def forceSwap(first: PlayerDirection, second: PlayerDirection) = SessionState(lobby.forceSwap(first, second), game)

    def setUserAsAssistant(user: User.Actor) = SessionState(lobby.setUserAsAssistant(user), game)

    def setAssistantLevel(level: Int) = SessionState(lobby.setAssistantLevel(level), game)

    def directionOf(user: User.Actor) = lobby.users(user).position

    def isTerminal = game.exists(_.impl.playerObservation(PlayerDirection.North).gameStage == Core.GameStage.Scoring)

    def startGame() = SessionState(lobby, Some(GameState()))

    def promoteUserToHost(user: User.Actor) = SessionState(lobby.promoteUserToHost(user), game)

    def playerObservation(player: User.Actor) = game.map(_.impl.playerObservation(directionOf(player)))

    def currentPlayer: Option[User.Actor] =
      game.flatMap { game =>
        val direction = game.impl.currentPlayer
        lobby.users.find(_._2.position == direction).map(_._1)
      }

    def playAction(action: Core.Action): Either[IllegalAction, SessionState] =
      game match
        case Some(game) =>
          try {
            // Note: this is not a pure function
            game.impl.step(action)
            Right(SessionState(lobby, Some(game)))
          } catch {
            case _ => Left(IllegalAction)
          }
        case None => Left(IllegalAction)

  private object SessionState:
    def apply(host: User.Actor): SessionState = SessionState(LobbyState(host), None)

  case object SessionFull
  type SessionFull = SessionFull.type

  case object IllegalAction
  type IllegalAction = IllegalAction.type

  final case class PlayerInfo(
    id: User.Id,
    ready: Boolean,
    position: PlayerDirection,
    isHuman: Boolean,
  )
  final case class SessionInfo(
    host: User.Id,
    users: List[PlayerInfo],
    started: Boolean,
    playerObservation: Option[Core.PlayerObservation],
    assistantLevel: Int,
  )

  sealed trait Command
  final case class GetId(replyTo: ActorRef[Id]) extends Command
  final case class RemoveUser(user: User.Actor, replyTo: ActorRef[Unit]) extends Command
  final case class KickUser(kicker: User.Actor, kicked: User.Actor, replyTo: ActorRef[Unit]) extends Command
  final case class GetInfo(user: User.Actor, replyTo: ActorRef[SessionInfo]) extends Command
  final case class AddSubscriber(user: User.Actor, replyTo: ActorRef[SessionInfo]) extends Command
  final case class SetUserAsAssistant(user: User.Actor, replyTo: ActorRef[Unit]) extends Command

  sealed trait LobbyCommand extends Command
  final case class AddUser(user: User.Actor, replyTo: ActorRef[Either[SessionFull, Unit]]) extends LobbyCommand
  final case class SetUserReady(user: User.Actor, ready: Boolean, replyTo: ActorRef[Unit]) extends LobbyCommand
  final case class ForceSwap(first: PlayerDirection, second: PlayerDirection, replyTo: ActorRef[Unit]) extends LobbyCommand
  final case class SetAssistantLevel(level: Int, replyTo: ActorRef[Unit]) extends LobbyCommand
  final case class PromoteUserToHost(promoter: User.Actor, promotee: User.Actor, replyTo: ActorRef[Either[Backend.UserNotInSession, Unit]]) extends LobbyCommand

  sealed trait GameCommand extends Command
  final case class PlayAction(user: User.Actor, action: Core.Action, replyTo: ActorRef[Either[IllegalAction, Unit]]) extends GameCommand

  private final case class UserDied(user: User.Actor) extends Command
  private final case class SubscriberDied(subscriber: ActorRef[SessionInfo]) extends Command
  private case object NotifySubscribers extends Command

  given akka.util.Timeout = akka.util.Timeout(1000, java.util.concurrent.TimeUnit.MILLISECONDS)
  private val notifySubscribersInterval = 2.second

  def apply(id: Id): Behavior[Command] =
    Behaviors.setup { context =>
      context.scheduleOnce(notifySubscribersInterval, context.self, NotifySubscribers)
      empty(id)
    }

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
          lobby(id, state, Map.empty)

        case RemoveUser(user, replyTo) =>
          context.log.error("RemoveUser: no users")
          Behaviors.unhandled

        case KickUser(kicker, kicked, replyTo) =>
          context.log.error("KickUser: no users")
          Behaviors.unhandled

        case SetUserReady(user, ready, replyTo) =>
          context.log.error("SetUserReady: no users")
          Behaviors.unhandled

        case ForceSwap(first, second, replyTo) =>
          context.log.error("ForceSwap: no users")
          Behaviors.unhandled

        case SetUserAsAssistant(user, replyTo) =>
          context.log.error("SetUserAsAssistant: no users")
          Behaviors.unhandled

        case SetAssistantLevel(level, replyTo) =>
          context.log.error("SetAssistantLevel: no users")
          Behaviors.unhandled

        case GetInfo(user, replyTo) =>
          context.log.error("GetInfo: empty session")
          Behaviors.unhandled

        case GetId(replyTo) =>
          context.log.debug("GetId")
          replyTo ! id
          Behaviors.same

        case UserDied(user) =>
          context.log.error("UserDied - no users")
          Behaviors.unhandled

        case AddSubscriber(user, replyTo) =>
          context.log.error("AddSubscriber: empty session")
          Behaviors.unhandled

        case SubscriberDied(subscriber) =>
          context.log.error("SubscriberDied: empty session")
          Behaviors.unhandled

        case NotifySubscribers =>
          context.log.debug("NotifySubscribers")
          context.scheduleOnce(notifySubscribersInterval, context.self, NotifySubscribers)
          Behaviors.same

        case _: GameCommand =>
          context.log.error("GameCommand: empty session")
          Behaviors.unhandled

        case PromoteUserToHost(promoter, promotee, replyTo) =>
          context.log.error("PromoteUserToHost: empty session")
          Behaviors.unhandled
      }
    }

  private def lobby(id: Id, state: SessionState, subs: Map[ActorRef[SessionInfo], User.Actor]): Behavior[Command] =
    Behaviors.setup { context =>
      given ActorSystem[_] = context.system
      given ExecutionContext = context.executionContext
      context.setLoggerName(s"agh.bridge.back.Session-$id [lobby]")
      context.watchWith(state.lobby.host, UserDied(state.lobby.host))
      Behaviors.receiveMessage {

        case AddUser(user, replyTo) if state.lobby.users.size < 4 =>
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

        case RemoveUser(user, replyTo) if state.lobby.users.contains(user) && state.lobby.users.size > 1 =>
          context.log.debug("RemoveUser")
          val newState = state.removeUser(user)
          replyTo ! ()
          context.unwatch(user)
          notifySubscribers(subs, newState)
          lobby(id, newState, subs)

        case RemoveUser(user, replyTo) if state.lobby.users.contains(user) =>
          context.log.debug("RemoveUser - last user")
          replyTo ! ()
          Behaviors.stopped

        case RemoveUser(user, replyTo) =>
          context.log.error("RemoveUser - user not in session")
          Behaviors.unhandled

        case KickUser(kicker, kicked, replyTo) if kicker == state.lobby.host && kicked != state.lobby.host && state.lobby.users.contains(kicked) =>
          context.log.debug("KickUser")
          context.self ! RemoveUser(kicked, replyTo)
          Behaviors.same

        case KickUser(kicker, kicked, replyTo) =>
          context.log.error("KickUser - bad conditions")
          Behaviors.unhandled

        case SetUserReady(user, ready, replyTo) if state.lobby.users.contains(user) =>
          context.log.debug("SetUserReady")
          val newState = state.setReady(user, ready)
          replyTo ! Right(())
          notifySubscribers(subs, newState)
          if newState.lobby.allReady then
            context.log.debug("SetUserReady - starting game")
            game(id, newState.startGame(), subs)
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

        case SetUserAsAssistant(user, replyTo) if state.lobby.users.contains(user) =>
          context.log.debug("SetUserAsAssistant")
          val newState = state.setUserAsAssistant(user)
          replyTo ! ()
          notifySubscribers(subs, newState)
          lobby(id, newState, subs)

        case SetUserAsAssistant(user, replyTo) =>
          context.log.error("SetUserAsAssistant - user not in session")
          Behaviors.unhandled

        case SetAssistantLevel(level, replyTo) =>
          context.log.debug("SetAssistantLevel")
          val newState = state.setAssistantLevel(level)
          replyTo ! ()
          notifySubscribers(subs, newState)
          lobby(id, newState, subs)

        case GetInfo(user, replyTo) =>
          context.log.debug("GetInfo")
          sendInfo(user, replyTo, state)
          Behaviors.same

        case GetId(replyTo) =>
          context.log.debug("GetId")
          replyTo ! id
          Behaviors.same

        case UserDied(user) if state.lobby.users.contains(user) && state.lobby.users.nonEmpty =>
          context.log.error("UserDied")
          val newState = state.removeUser(user)
          notifySubscribers(subs, newState)
          lobby(id, newState, subs)

        case UserDied(user) if state.lobby.users.contains(user) =>
          context.log.error("UserDied - last user")
          Behaviors.stopped

        case UserDied(user) =>
          context.log.error("UserDied - user not in session")
          Behaviors.unhandled

        case AddSubscriber(user, replyTo) =>
          context.log.debug("AddSubscriber")
          context.watchWith(replyTo, SubscriberDied(replyTo))
          sendInfo(user, replyTo, state)
          lobby(id, state, subs + (replyTo -> user))

        case SubscriberDied(subscriber) =>
          context.log.debug("SubscriberDied")
          lobby(id, state, subs.removed(subscriber))

        case NotifySubscribers =>
          context.log.debug("NotifySubscribers")
          context.scheduleOnce(notifySubscribersInterval, context.self, NotifySubscribers)
          notifySubscribers(subs, state)
          Behaviors.same

        case _: GameCommand =>
          context.log.error("GameCommand: lobby session")
          Behaviors.unhandled

        case PromoteUserToHost(promoter, promotee, replyTo) if promoter == state.lobby.host && state.lobby.users.contains(promotee) =>
          context.log.debug("PromoteUserToHost")
          val newState = state.promoteUserToHost(promotee)
          replyTo ! Right(())
          notifySubscribers(subs, newState)
          lobby(id, newState, subs)

        case PromoteUserToHost(promoter, promotee, replyTo) =>
          context.log.error("PromoteUserToHost - bad conditions")
          replyTo ! Left(Backend.UserNotInSession)
          Behaviors.same
      }
    }

  private def game(id: Id, state: SessionState, subs: Map[ActorRef[SessionInfo], User.Actor]): Behavior[Command] =
    Behaviors.setup { context =>
      given ActorSystem[_] = context.system
      given ExecutionContext = context.executionContext
      context.setLoggerName(s"agh.bridge.back.Session-$id [game]")
      Behaviors.receiveMessage {

        case PlayAction(user, action, replyTo) =>
          context.log.debug("PlayAction")
          // This logic did not take into account the dummy player
          // if Some(user) != state.currentPlayer then
          //   context.log.error("PlayAction - not current player")
          //   replyTo ! Left(IllegalAction)
          //   Behaviors.same
          // else
          state.playAction(action) match
            case Left(IllegalAction) =>
              context.log.error("PlayAction - illegal action")
              replyTo ! Left(IllegalAction)
              Behaviors.same
            case Right(newState) =>
              replyTo ! Right(())
              notifySubscribers(subs, newState)
              if newState.isTerminal then
                context.log.debug("PlayAction - game ended")
                lobby(id, newState, subs)
              else
                if (!state.lobby.users(newState.currentPlayer.get).isHuman) {
                  context.log.debug("Next player is assistant")
                  context.scheduleOnce(1.second, context.self, PlayAction(newState.currentPlayer.get, Core.AssistantAction, context.system.ignoreRef))
                }
                game(id, newState, subs)

        case AddUser(user, replyTo) =>
          context.log.error("AddUser - game already started")
          Behaviors.unhandled

        case RemoveUser(user, replyTo) =>
          context.log.debug("RemoveUser - killing game")
          replyTo ! ()
          Behaviors.stopped

        case KickUser(kicker, kicked, replyTo) if kicker == state.lobby.host && kicked != state.lobby.host && state.lobby.users.contains(kicked) =>
          context.log.debug("KickUser")
          context.self ! RemoveUser(kicked, replyTo)
          Behaviors.same

        case KickUser(kicker, kicked, replyTo) =>
          context.log.error("KickUser - bad conditions")
          Behaviors.unhandled

        case SetUserReady(user, ready, replyTo) =>
          context.log.error("SetUserReady - game already started")
          Behaviors.unhandled

        case ForceSwap(first, second, replyTo) =>
          context.log.error("ForceSwap - game already started")
          Behaviors.unhandled

        case SetUserAsAssistant(user, replyTo) if state.lobby.users.contains(user) =>
          context.log.debug("SetUserAsAssistant")
          val newState = state.setUserAsAssistant(user)
          replyTo ! ()
          notifySubscribers(subs, newState)
          lobby(id, newState, subs)

        case SetUserAsAssistant(user, replyTo) =>
          context.log.error("SetUserAsAssistant - user not in session")
          Behaviors.unhandled

        case SetAssistantLevel(level, replyTo) =>
          context.log.error("SetAssistantLevel - game already started")
          Behaviors.unhandled

        case GetInfo(user, replyTo) =>
          context.log.debug("GetInfo")
          sendInfo(user, replyTo, state)
          Behaviors.same

        case GetId(replyTo) =>
          context.log.debug("GetId")
          replyTo ! id
          Behaviors.same

        case UserDied(user) =>
          context.log.error("UserDied - killing game")
          Behaviors.stopped

        case AddSubscriber(user, replyTo) =>
          context.log.debug("AddSubscriber")
          context.watchWith(replyTo, SubscriberDied(replyTo))
          sendInfo(user, replyTo, state)
          game(id, state, subs + (replyTo -> user))

        case SubscriberDied(subscriber) =>
          context.log.debug("SubscriberDied")
          game(id, state, subs.removed(subscriber))

        case NotifySubscribers =>
          context.log.debug("NotifySubscribers")
          context.scheduleOnce(notifySubscribersInterval, context.self, NotifySubscribers)
          notifySubscribers(subs, state)
          Behaviors.same

        case PromoteUserToHost(promoter, promotee, replyTo) =>
          context.log.error("PromoteUserToHost - game already started")
          Behaviors.unhandled
      }
    }

  private def sendInfo(user: User.Actor, replyTo: ActorRef[SessionInfo], state: SessionState)(using ActorSystem[_], ExecutionContext): Unit =
    val host = state.lobby.host
    val ready = state.lobby.users.values.map(_.ready)
    val position = state.lobby.users.values.map(_.position)
    val isHuman = state.lobby.users.values.map(_.isHuman)
    for
      hostId <- host.ask[User.Id](User.GetId(_))
      playerIds <- Future.sequence(state.lobby.users.keys.map { user =>
                     user.ask[User.Id](User.GetId(_))
                   })
    do
      val info = SessionInfo(
        hostId,
        (playerIds zip ready zip position zip isHuman).map { case (((id, ready), position), isHuman) =>
          PlayerInfo(id, ready, position, isHuman)
        }.toList,
        state.lobby.allReady,
        state.playerObservation(user),
        state.lobby.assistantLevel,
      )
      replyTo ! info

  private def notifySubscribers(subs: Map[ActorRef[SessionInfo], User.Actor], state: SessionState)(using ActorSystem[_], ExecutionContext): Unit =
    subs.foreach((replyTo, user) => sendInfo(user, replyTo, state))
}
