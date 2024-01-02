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

import agh.bridge.core as Core
import agh.bridge.core.PlayerDirection
import agh.bridge.back.CorsHandler.cors
import agh.bridge.back.AuthHandler.requireAuthentication
import agh.bridge.firebase.Authentication

object HttpServer {

  import GameStateModels.given

  // --- / ---

  // POST /heartbeat
  // heartbeat the logged in user


  // --- /session ---

  // GET /session/info
  // get info about the session the logged in user is in

  private final case class PlayerModel(
    id: User.Id,
    ready: Boolean,
    position: Int,
    displayName: Option[String],
    isHuman: Boolean,
  )
  private given RootJsonFormat[PlayerModel] = jsonFormat5(PlayerModel.apply)

  private final case class GetSessionInfoResponse(
    sessionId: Session.Id,
    hostId: User.Id,
    users: List[PlayerModel],
    started: Boolean,
    gameState: Option[GameStateModels.GameState],
    assistantLevel: Int,
  )
  private given RootJsonFormat[GetSessionInfoResponse] = jsonFormat6(GetSessionInfoResponse.apply)

  // POST /session/leave
  // leave the session the logged in user is in

  // POST /session/set-ai-level
  // set the AI level in the user's session

  private final case class SetAssistantLevelRequest(level: Int)
  private given RootJsonFormat[SetAssistantLevelRequest] = jsonFormat1(SetAssistantLevelRequest.apply)


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

  // POST /session/lobby/promote-host
  // promote the specified player to be the host of the game the logged in user is in

  private final case class PromoteHostRequest(userId: User.Id)
  private given RootJsonFormat[PromoteHostRequest] = jsonFormat1(PromoteHostRequest.apply)

  // POST /session/lobby/set-assistant
  // set the specified position to be an AI assistant in the game the logged in user is in

  private final case class SetAssistantRequest(direction: Int)
  private given RootJsonFormat[SetAssistantRequest] = jsonFormat1(SetAssistantRequest.apply)

  // POST /session/lobby/ready
  // set the ready status of the logged in user in his game

  private final case class SetUserReadyRequest(ready: Boolean)
  private given RootJsonFormat[SetUserReadyRequest] = jsonFormat1(SetUserReadyRequest.apply)

  // POST /session/lobby/kick
  // kick the specified player from the game the logged in user is in

  private final case class KickRequest(id: User.Id)
  private given RootJsonFormat[KickRequest] = jsonFormat1(KickRequest.apply)


  // --- /session/game ---

  // POST /session/game/play
  // play an action in the game the logged in user is in

  private final case class PlayActionRequest(suit: Core.Suit, rank: Core.Rank):
    def action: Core.Action = Core.Play(Core.Card(suit, rank))
  private given RootJsonFormat[PlayActionRequest] = jsonFormat2(PlayActionRequest.apply)

  // POST /session/game/pass
  // pass in the game the logged in user is in

  // POST /session/game/double
  // double in the game the logged in user is in

  // POST /session/game/redouble
  // redouble in the game the logged in user is in

  // POST /session/game/bid
  // bid in the game the logged in user is in

  private final case class BidRequest(tricks: Core.BidLevel, suit: Core.BidSuit):
    def action: Core.Action = Core.Bid(tricks, suit)
  private given RootJsonFormat[BidRequest] = jsonFormat2(BidRequest.apply)


  def apply(backend: Backend.Actor): Behavior[Nothing] = Behaviors.setup { context =>
    given ActorSystem[_] = context.system
    given ExecutionContext = context.executionContext
    given akka.util.Timeout = akka.util.Timeout(1000, java.util.concurrent.TimeUnit.MILLISECONDS)

    def handleAction(userId: User.Id, action: Core.Action) =
      val resFut = backend.ask[Either[Backend.UserNotInSession | Session.IllegalAction, Unit]](Backend.PlayAction(userId, action, _))
      complete(resFut map {
        case Right(()) => StatusCodes.OK
        case Left(Backend.UserNotInSession) => StatusCodes.NotFound
        case Left(Session.IllegalAction) => StatusCodes.BadRequest
      })

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
                          info.users map { user => PlayerModel(
                            user.id,
                            user.ready,
                            user.position.ordinal,
                            Some(s"User ${user.position.toString()}"),
                            user.isHuman,
                          ) },
                          info.started,
                          GameStateModels.GameState(userId, info),
                          info.assistantLevel,
                        )
                        ws.TextMessage(resp.toJson.compactPrint)
                      }.mapMaterializedValue(backend ! Backend.SubscribeToSessionInfo(sessionId, userId, _))
                      val flow = Flow.fromSinkAndSourceCoupledMat(Sink.ignore, source)(Keep.right)
                      handleWebSocketMessages(flow)
                  }
                },
                (path("leave") & post) {
                  val resFut = backend.ask[Unit](Backend.LeaveSession(userId, _))
                  complete(resFut map (_ => StatusCodes.OK))
                },
                (path("set-ai-level") & post & entity(as[SetAssistantLevelRequest])) { request =>
                  val sessionIdFut = backend.ask[Either[Backend.SessionNotFound, Session.Id]](Backend.FindSession(userId, _))
                  onSuccess(sessionIdFut) {
                    case Left(Backend.SessionNotFound) => complete(StatusCodes.NotFound)
                    case Right(sessionId) =>
                      val resFut = backend.ask[Either[Backend.SessionNotFound, Unit]](Backend.SetAssistantLevel(sessionId, request.level, _))
                      complete(resFut map {
                        case Right(()) => StatusCodes.OK
                        case Left(Backend.SessionNotFound) => StatusCodes.NotFound
                      })
                  }
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
                    (path("promote-host") & post & entity(as[PromoteHostRequest])) { request =>
                      val resFut = backend.ask[Either[Backend.UserNotInSession, Unit]](Backend.PromoteUserToHost(userId, request.userId, _))
                      complete(resFut map {
                        case Right(()) => StatusCodes.OK
                        case Left(Backend.UserNotInSession) => StatusCodes.NotFound
                      })
                    },
                    (path("set-assistant") & post & entity(as[SetAssistantRequest])) { request =>
                      val sessionIdFut = backend.ask[Either[Backend.SessionNotFound, Session.Id]](Backend.FindSession(userId, _))
                      onSuccess(sessionIdFut) {
                        case Left(Backend.SessionNotFound) => complete(StatusCodes.NotFound)
                        case Right(sessionId) =>
                          val resFut = backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.AddAssistant(sessionId, PlayerDirection.fromOrdinal(request.direction), _))
                          complete(resFut map {
                            case Right(()) => StatusCodes.OK
                            case Left(Backend.SessionNotFound) => StatusCodes.NotFound
                            case Left(Session.SessionFull) => StatusCodes.Conflict
                          })
                      }
                    },
                    (path("ready") & post & entity(as[SetUserReadyRequest])) { request =>
                      val resFut = backend.ask[Either[Backend.UserNotInSession, Unit]](Backend.SetUserReady(userId, request.ready, _))
                      complete(resFut map {
                        case Right(()) => StatusCodes.OK
                        case Left(Backend.UserNotInSession) => StatusCodes.NotFound
                      })
                    },
                    (path("kick") & post & entity(as[KickRequest])) { request =>
                      val resFut = backend.ask[Either[Backend.UserNotInSession, Unit]](Backend.KickUser(userId, request.id, _))
                      complete(resFut map {
                        case Right(()) => StatusCodes.OK
                        case Left(Backend.UserNotInSession) => StatusCodes.NotFound
                      })
                    },
                  )
                },
                pathPrefix("game") {
                  concat(
                    path("play") {
                      post {
                        entity(as[PlayActionRequest]) { request =>
                          handleAction(userId, request.action)
                        }
                      }
                    },
                    path("pass") {
                      post {
                        handleAction(userId, Core.Pass)
                      }
                    },
                    path("double") {
                      post {
                        handleAction(userId, Core.Double)
                      }
                    },
                    path("redouble") {
                      post {
                        handleAction(userId, Core.Redouble)
                      }
                    },
                    path("bid") {
                      post {
                        entity(as[BidRequest]) { request =>
                          handleAction(userId, request.action)
                        }
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
