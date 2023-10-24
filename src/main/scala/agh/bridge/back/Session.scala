package agh.bridge.back

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.actor.typed.scaladsl.AskPattern._

object Session {
  type Actor = ActorRef[Command]
  type Id = String

  sealed trait Command
  final case class AddUser(user: User.Actor, replyTo: ActorRef[Either[AddUserError, Unit]]) extends Command
  final case class RemoveUser(user: User.Actor) extends Command
  final case class GetLobbyInfo(replyTo: ActorRef[LobbyInfo]) extends Command
  final case class GetId(replyTo: ActorRef[Id]) extends Command

  final case class Player(id: User.Id, ready: Boolean, position: Int)
  final case class LobbyInfo(host: User.Id, users: List[Player], started: Boolean)

  case object SessionFull

  type AddUserError = SessionFull.type

  def apply(id: Id): Behavior[Command] = session(id, List.empty)

  private def session(id: Id, users: List[User.Actor]): Behavior[Command] =
    Behaviors.setup { context =>
      given ActorSystem[_] = context.system
      given ExecutionContext = context.executionContext
      given akka.util.Timeout = akka.util.Timeout(1000, java.util.concurrent.TimeUnit.MILLISECONDS)
      Behaviors.receiveMessage {

        case AddUser(user, replyTo) =>
          if (users.length < 4) {
            replyTo ! Right(())
            session(id, users :+ user)
          } else {
            replyTo ! Left(SessionFull)
            Behaviors.same
          }

        case RemoveUser(user) => session(id, users filterNot(_ == user))

        case GetLobbyInfo(replyTo) =>
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
          replyTo ! id
          Behaviors.same
      }
    }
}
