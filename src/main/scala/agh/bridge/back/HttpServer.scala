package agh.bridge.back

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.actor.typed.scaladsl.AskPattern._

import akka.stream._
import akka.stream.scaladsl._
import akka.stream.typed.scaladsl.{ ActorSource, ActorSink }

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

import agh.bridge.core.PlayerDirection
import agh.bridge.back.CorsHandler.cors

object HttpServer {

  // POST /session/heartbeat

  private final case class HeartbeatRequest(userId: User.Id)
  private given RootJsonFormat[HeartbeatRequest] = jsonFormat1(HeartbeatRequest.apply)

  // GET /session/find

  private final case class FindSessionResponse(sessionId: Session.Id)
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

  // POST /session/lobby/force-swap

  private final case class ForceSwapRequest(sessionId: Session.Id, first: Int, second: Int)
  private given RootJsonFormat[ForceSwapRequest] = jsonFormat3(ForceSwapRequest.apply)

  // POST /session/lobby/ready

  private final case class SetUserReadyRequest(userId: User.Id, ready: Boolean)
  private given RootJsonFormat[SetUserReadyRequest] = jsonFormat2(SetUserReadyRequest.apply)


  def apply(backend: Backend.Actor): Behavior[Nothing] = Behaviors.setup { context =>
    given ActorSystem[_] = context.system
    given ExecutionContext = context.executionContext
    given akka.util.Timeout = akka.util.Timeout(1000, java.util.concurrent.TimeUnit.MILLISECONDS)

    val route = cors(
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
                  backend ! Backend.Heartbeat(request.userId)
                  complete(StatusCodes.OK)
                }
              }
            },
            path("find") {
              get {
                parameter("userId".as[User.Id]) { userId =>
                  val sessionIdOptFut = backend.ask[Either[Backend.SessionNotFound, Session.Id]](Backend.FindSession(userId, _))
                  onSuccess(sessionIdOptFut) {
                    case Right(sessionId) => complete(FindSessionResponse(sessionId))
                    case Left(Backend.SessionNotFound) => complete(StatusCodes.NotFound)
                  }
                }
              }
            },
            pathPrefix("lobby") {
              concat(
                path("create") {
                  post {
                    entity(as[CreateLobbyRequest]) { request =>
                      val sessionIdFut = backend.ask[Session.Id](Backend.CreateLobby(request.hostId, _))
                      complete(sessionIdFut map CreateLobbyResponse.apply)
                    }
                  }
                },
                path("join") {
                  post {
                    entity(as[JoinLobbyRequest]) { request =>
                      val resFut = backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby(request.userId, request.sessionId, _))
                      complete(resFut map {
                        case Left(Backend.SessionNotFound) => StatusCodes.NotFound
                        case Left(Session.SessionFull) => StatusCodes.Conflict
                        case Right(_) => StatusCodes.OK
                      })
                    }
                  }
                },
                path("leave") {
                  post {
                    entity(as[LeaveLobbyRequest]) { request =>
                      val resFut = backend.ask[Unit](Backend.LeaveSession(request.userId, _))
                      complete(resFut map (_ => StatusCodes.OK))
                    }
                  }
                },
                path("info") {
                  parameter("sessionId".as[Session.Id]) { sessionId =>
                    val source = ActorSource.actorRef[Session.SessionInfo](
                      completionMatcher = PartialFunction.empty,
                      failureMatcher = PartialFunction.empty,
                      bufferSize = 8,
                      overflowStrategy = OverflowStrategy.dropHead,
                    ).map { info =>
                      val resp = GetLobbyInfoResponse(
                        info.host,
                        info.users map { user => PlayerModel(user.id, user.ready, user.position.ordinal) },
                        info.started,
                      )
                      ws.TextMessage(resp.toJson.compactPrint)
                    }.mapMaterializedValue { ref =>
                      backend ! Backend.SubscribeToSessionInfo(sessionId, ref)
                    }
                    val flow = Flow.fromSinkAndSourceCoupledMat(Sink.ignore, source)(Keep.right)
                    handleWebSocketMessages(flow)
                  }
                },
                path("force-swap") {
                  post {
                    entity(as[ForceSwapRequest]) { request =>
                      val resFut = backend.ask[Either[Backend.SessionNotFound, Unit]](Backend.ForceSwap(request.sessionId, PlayerDirection.fromOrdinal(request.first), PlayerDirection.fromOrdinal(request.second), _))
                      complete(resFut map {
                        case Right(()) => StatusCodes.OK
                        case Left(Backend.SessionNotFound) => StatusCodes.NotFound
                      })
                    }
                  }
                },
                path("ready") {
                  post {
                    entity(as[SetUserReadyRequest]) { request =>
                      val resFut = backend.ask[Either[Backend.UserNotInSession, Unit]](Backend.SetUserReady(request.userId, request.ready, _))
                      complete(resFut map {
                        case Right(()) => StatusCodes.OK
                        case Left(Backend.UserNotInSession) => StatusCodes.Conflict
                      })
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
    )

    val bindingFuture = Http().newServerAt("0.0.0.0", 8000).bind(route)

    Behaviors.empty
  }
}
