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

object HttpServer {

  // POST /session/heartbeat

  private final case class HeartbeatRequest(userId: User.Id)
  private given RootJsonFormat[HeartbeatRequest] = jsonFormat1(HeartbeatRequest.apply)

  // GET /session/find

  private final case class FindSessionResponse(sessionId: Option[Session.Id])
  private given RootJsonFormat[FindSessionResponse] = jsonFormat1(FindSessionResponse.apply)

  // POST /session/lobby/create

  private final case class CreateLobbyRequest(hostId: User.Id)
  private given RootJsonFormat[CreateLobbyRequest] = jsonFormat1(CreateLobbyRequest.apply)

  private final case class CreateLobbyResponse(sessionId: Session.Id)
  private given RootJsonFormat[CreateLobbyResponse] = jsonFormat1(CreateLobbyResponse.apply)

  // POST /session/lobby/join

  private final case class JoinLobbyRequest(sessionId: Session.Id, userId: User.Id)
  private given RootJsonFormat[JoinLobbyRequest] = jsonFormat2(JoinLobbyRequest.apply)

  // POST /session/lobby/leave

  private final case class LeaveLobbyRequest(userId: User.Id)
  private given RootJsonFormat[LeaveLobbyRequest] = jsonFormat1(LeaveLobbyRequest.apply)

  // GET /session/lobby/info

  private final case class PlayerModel(id: User.Id, ready: Boolean, position: Int)
  private given RootJsonFormat[PlayerModel] = jsonFormat3(PlayerModel.apply)

  private final case class GetLobbyInfoResponse(hostId: User.Id, users: List[PlayerModel], started: Boolean)
  private given RootJsonFormat[GetLobbyInfoResponse] = jsonFormat3(GetLobbyInfoResponse.apply)

  // POST /session/lobby/ready

  private final case class SetUserReadyRequest(userId: User.Id, ready: Boolean)
  private given RootJsonFormat[SetUserReadyRequest] = jsonFormat2(SetUserReadyRequest.apply)


  def apply(backend: Backend.Actor): Behavior[Nothing] = Behaviors.setup { context =>
    given ActorSystem[_] = context.system
    given ExecutionContext = context.executionContext
    given akka.util.Timeout = akka.util.Timeout(1000, java.util.concurrent.TimeUnit.MILLISECONDS)

    val route =
      concat(
        path("healthcheck") {
          get {
            complete(HttpEntity(ContentTypes.`application/json`, """{"status": "ok"}"""))
          }
        },
        pathPrefix("session") {
          concat(
            path("heartbeat") {
              post {
                entity(as[HeartbeatRequest]) { request =>
                  complete(StatusCodes.NotImplemented)
                }
              }
            },
            path("find") {
              get {
                parameter("userId".as[User.Id]) { userId =>
                  val session = backend.ask[Option[Session.Id]](Backend.FindSession(userId, _))
                  complete(session map (FindSessionResponse(_)))
                }
              }
            },
            pathPrefix("lobby") {
              concat(
                path("create") {
                  post {
                    entity(as[CreateLobbyRequest]) { request =>
                      val session = backend.ask[Session.Id](Backend.CreateLobby(request.hostId, _))
                      complete(session map (CreateLobbyResponse(_)))
                    }
                  }
                },
                path("join") {
                  post {
                    entity(as[JoinLobbyRequest]) { request =>
                      complete(StatusCodes.NotImplemented)
                    }
                  }
                },
                path("leave") {
                  post {
                    entity(as[LeaveLobbyRequest]) { request =>
                      complete(StatusCodes.NotImplemented)
                    }
                  }
                },
                path("info") {
                  get {
                    parameter("sessionId".as[Session.Id]) { sessionId =>
                      val infoOptFut = backend.ask[Option[Session.LobbyInfo]](Backend.GetLobbyInfo(sessionId, _))
                      val infoFut = infoOptFut flatMap {
                        case Some(info) => Future.successful(info)
                        case None => Future.failed(new Exception("Session not found"))
                      }
                      complete(infoFut map { info => GetLobbyInfoResponse(
                        info.host,
                        info.users map { user => PlayerModel(user.id, user.ready, user.position) },
                        info.started,
                      )})
                    }
                  }
                },
                path("ready") {
                  post {
                    entity(as[SetUserReadyRequest]) { request =>
                      complete(StatusCodes.NotImplemented)
                    }
                  }
                },
              )
            },
            pathPrefix("game") {
              concat(
                path("info") {
                  get {
                    complete(StatusCodes.NotImplemented)
                  }
                },
                path("play") {
                  post {
                    complete(StatusCodes.NotImplemented)
                  }
                },
                path("pass") {
                  post {
                    complete(StatusCodes.NotImplemented)
                  }
                },
                path("double") {
                  post {
                    complete(StatusCodes.NotImplemented)
                  }
                },
                path("bid") {
                  post {
                    complete(StatusCodes.NotImplemented)
                  }
                },
              )
            },
          )
        },
      )

    val bindingFuture = Http().newServerAt("localhost", 8000).bind(route)

    Behaviors.empty
  }
}
