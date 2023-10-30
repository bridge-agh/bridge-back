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
import agh.bridge.back.AuthHandler.requireAuthentication
import agh.bridge.firebase.Authentication

object HttpServer {

  // --- / ---

  // POST /heartbeat
  // heartbeat the logged in user


  // --- /session ---

  // GET /session/info
  // get info about the session the logged in user is in

  private final case class PlayerModel(id: User.Id, ready: Boolean, position: Int)
  private given RootJsonFormat[PlayerModel] = jsonFormat3(PlayerModel.apply)

  private final case class GetSessionInfoResponse(sessionId: Session.Id, hostId: User.Id, users: List[PlayerModel], started: Boolean)
  private given RootJsonFormat[GetSessionInfoResponse] = jsonFormat4(GetSessionInfoResponse.apply)

  // POST /session/leave
  // leave the session the logged in user is in


  // --- /session/lobby ---

  // POST /session/lobby/create
  // logged in user creates a new game

  private final case class CreateLobbyResponse(sessionId: Session.Id)
  private given RootJsonFormat[CreateLobbyResponse] = jsonFormat1(CreateLobbyResponse.apply)

  // POST /session/lobby/join
  // logged in user joins a game

  private final case class JoinLobbyRequest(sessionId: Session.Id)
  private given RootJsonFormat[JoinLobbyRequest] = jsonFormat1(JoinLobbyRequest.apply)

  // POST /session/lobby/force-swap
  // force swap two players in the game the logged in user is in

  private final case class ForceSwapRequest(first: Int, second: Int)
  private given RootJsonFormat[ForceSwapRequest] = jsonFormat2(ForceSwapRequest.apply)

  // POST /session/lobby/ready
  // set the ready status of the logged in user in his game

  private final case class SetUserReadyRequest(ready: Boolean)
  private given RootJsonFormat[SetUserReadyRequest] = jsonFormat1(SetUserReadyRequest.apply)


  def apply(backend: Backend.Actor): Behavior[Nothing] = Behaviors.setup { context =>
    given ActorSystem[_] = context.system
    given ExecutionContext = context.executionContext
    given akka.util.Timeout = akka.util.Timeout(1000, java.util.concurrent.TimeUnit.MILLISECONDS)

    val route = cors(
      concat(
        (path("healthcheck") & get) {
          complete(HttpEntity(ContentTypes.`application/json`, """{"status": "ok"}"""))
        },
        requireAuthentication { userId =>
          concat(
            (path("heartbeat") & post) {
              backend ! Backend.Heartbeat(userId)
              complete(StatusCodes.OK)
            },
            pathPrefix("session") {
              concat(
                path("info") {
                  val sessionIdFut = backend.ask[Either[Backend.SessionNotFound, Session.Id]](Backend.FindSession(userId, _))
                  onSuccess(sessionIdFut) {
                    case Left(Backend.SessionNotFound) => complete(StatusCodes.NotFound)
                    case Right(sessionId) =>
                      val source = ActorSource.actorRef[Session.SessionInfo](
                        completionMatcher = PartialFunction.empty,
                        failureMatcher = PartialFunction.empty,
                        bufferSize = 8,
                        overflowStrategy = OverflowStrategy.dropHead,
                      ).map { info =>
                        val resp = GetSessionInfoResponse(
                          sessionId,
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
                (path("leave") & post) {
                  val resFut = backend.ask[Unit](Backend.LeaveSession(userId, _))
                  complete(resFut map (_ => StatusCodes.OK))
                },
                pathPrefix("lobby") {
                  concat(
                    (path("create") & post) {
                      val sessionIdFut = backend.ask[Session.Id](Backend.CreateLobby(userId, _))
                      complete(sessionIdFut map CreateLobbyResponse.apply)
                    },
                    (path("join") & post & entity(as[JoinLobbyRequest])) { request =>
                      val resFut = backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby(userId, request.sessionId, _))
                      complete(resFut map {
                        case Left(Backend.SessionNotFound) => StatusCodes.NotFound
                        case Left(Session.SessionFull) => StatusCodes.Conflict
                        case Right(()) => StatusCodes.OK
                      })
                    },
                    (path("force-swap") & post & entity(as[ForceSwapRequest])) { request =>
                      val sessionIdFut = backend.ask[Either[Backend.SessionNotFound, Session.Id]](Backend.FindSession(userId, _))
                      onSuccess(sessionIdFut) {
                        case Left(Backend.SessionNotFound) => complete(StatusCodes.NotFound)
                        case Right(sessionId) =>
                          val resFut = backend.ask[Either[Backend.SessionNotFound, Unit]](Backend.ForceSwap(sessionId, PlayerDirection.fromOrdinal(request.first), PlayerDirection.fromOrdinal(request.second), _))
                          complete(resFut map {
                            case Right(()) => StatusCodes.OK
                            case Left(Backend.SessionNotFound) => StatusCodes.NotFound
                          })
                      }
                    },
                    (path("ready") & post & entity(as[SetUserReadyRequest])) { request =>
                      val resFut = backend.ask[Either[Backend.UserNotInSession, Unit]](Backend.SetUserReady(userId, request.ready, _))
                      complete(resFut map {
                        case Right(()) => StatusCodes.OK
                        case Left(Backend.UserNotInSession) => StatusCodes.Conflict
                      })
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
        },
      )
    )

    val bindingFuture = Http().newServerAt("0.0.0.0", 8000).bind(route)

    Behaviors.empty
  }
}
