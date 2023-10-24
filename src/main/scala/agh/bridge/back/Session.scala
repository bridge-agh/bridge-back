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

object Session {
  type Actor = ActorRef[Command]
  type Id = String

  sealed trait Command
  final case class AddUser(user: User.Actor, replyTo: ActorRef[Either[SessionFull.type, Unit]]) extends Command
  final case class RemoveUser(user: User.Actor, replyTo: ActorRef[Either[UserNotInSession.type, Unit]]) extends Command
  final case class GetLobbyInfo(replyTo: ActorRef[LobbyInfo]) extends Command
  final case class GetId(replyTo: ActorRef[Id]) extends Command

  case object SessionFull
  case object UserNotInSession
  final case class Player(id: User.Id, ready: Boolean, position: Int)
  final case class LobbyInfo(host: User.Id, users: List[Player], started: Boolean)

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

        case RemoveUser(user, replyTo) =>
          val newUsers = users.filterNot(_ == user)
          if (newUsers.length < users.length) {
            replyTo ! Right(())
            session(id, newUsers)
          } else {
            replyTo ! Left(UserNotInSession)
            Behaviors.same
          }

        case GetLobbyInfo(replyTo) =>
          val started = false
          val host = users.head
          val hostIdFut = host.ask[User.Id](User.GetId(_))
          val idsFut = Future.sequence(users map { user =>
            user.ask[User.Id](User.GetId(_))
          })
          val readyEitherFut =  Future.sequence(users map { user =>
            user.ask[Either[UserNotInSession.type, Boolean]](User.GetReady(_))
          })
          val readyFut = readyEitherFut flatMap { readyEither =>
            Future.sequence(readyEither map {
              case Right(ready) => Future.successful(ready)
              case Left(_) => Future.failed(new Exception("User should be in session"))
            })
          }
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
