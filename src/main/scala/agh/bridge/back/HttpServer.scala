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

object GameStateModels {
  given RootJsonFormat[Core.GameStage] = new RootJsonFormat[Core.GameStage] {
    def write(stage: Core.GameStage): JsValue = JsNumber(stage.ordinal)
    def read(value: JsValue): Core.GameStage = value match
      case JsNumber(num) => Core.GameStage.fromOrdinal(num.intValue)
      case _ => throw DeserializationException("Expected GameStage")
  }

  given RootJsonFormat[PlayerDirection] = new RootJsonFormat[PlayerDirection] {
    def write(direction: PlayerDirection): JsValue = JsNumber(direction.ordinal)
    def read(value: JsValue): PlayerDirection = value match
      case JsNumber(num) => PlayerDirection.fromOrdinal(num.intValue)
      case _ => throw DeserializationException("Expected PlayerDirection")
  }

  given RootJsonFormat[Core.Suit] = new RootJsonFormat[Core.Suit] {
    def write(suit: Core.Suit): JsValue = JsNumber(suit.ordinal)
    def read(value: JsValue): Core.Suit = value match
      case JsNumber(num) => Core.Suit.fromOrdinal(num.intValue)
      case _ => throw DeserializationException("Expected Suit")
  }

  given RootJsonFormat[Core.Rank] = new RootJsonFormat[Core.Rank] {
    def write(rank: Core.Rank): JsValue = JsNumber(rank.ordinal + 2)
    def read(value: JsValue): Core.Rank = value match
      case JsNumber(num) => Core.Rank.fromOrdinal(num.intValue - 2)
      case _ => throw DeserializationException("Expected Rank")
  }

  given RootJsonFormat[Core.BidLevel] = new RootJsonFormat[Core.BidLevel] {
    def write(level: Core.BidLevel): JsValue = JsNumber(level.ordinal + 1)
    def read(value: JsValue): Core.BidLevel = value match
      case JsNumber(num) => Core.BidLevel.fromOrdinal(num.intValue - 1)
      case _ => throw DeserializationException("Expected BidLevel")
  }

  given RootJsonFormat[Core.BidSuit] = new RootJsonFormat[Core.BidSuit] {
    def write(suit: Core.BidSuit): JsValue = JsNumber(suit.ordinal)
    def read(value: JsValue): Core.BidSuit = value match
      case JsNumber(num) => Core.BidSuit.fromOrdinal(num.intValue)
      case _ => throw DeserializationException("Expected BidSuit")
  }

  given RootJsonFormat[Core.PairDirection] = new RootJsonFormat[Core.PairDirection] {
    def write(direction: Core.PairDirection): JsValue = JsString(direction.toString)
    def read(value: JsValue): Core.PairDirection = value match
      case JsString(str) => Core.PairDirection.valueOf(str)
      case _ => throw DeserializationException("Expected PairDirection")
  }

  given RootJsonFormat[Core.Bid] = jsonFormat2(Core.Bid.apply)

  given RootJsonFormat[Core.Call] = new RootJsonFormat[Core.Call] {
    def write(call: Core.Call): JsValue = call match
      case Core.Pass => JsNumber(0)
      case Core.Double => JsNumber(1)
      case Core.Redouble => JsNumber(2)
      case bid: Core.Bid => bid.toJson
    def read(value: JsValue): Core.Call = value match
      case JsNumber(0) => Core.Pass
      case JsNumber(1) => Core.Double
      case JsNumber(2) => Core.Redouble
      case obj: JsObject => obj.convertTo[Core.Bid]
      case _ => throw DeserializationException("Expected Call")
  }

  given RootJsonFormat[Core.Card] = jsonFormat2(Core.Card.apply)

  given RootJsonFormat[Core.Play] = jsonFormat1(Core.Play.apply)

  final case class Trick(
    round_player: PlayerDirection,
    winner: PlayerDirection,
    cards: List[Core.Card],
  )
  given RootJsonFormat[Trick] = jsonFormat3(Trick.apply)

  final case class BaseObservation(
    game_stage: Core.GameStage,
    current_player: PlayerDirection,
    user_direction: PlayerDirection,
  )
  given RootJsonFormat[BaseObservation] = jsonFormat3(BaseObservation.apply)

  final case class BiddingObservation(
    first_dealer: PlayerDirection,
    bid_history: List[Core.Call],
    bid: Option[Core.Bid],
    declarer: Option[PlayerDirection],
    multiplier: Int,
  )
  given RootJsonFormat[BiddingObservation] = jsonFormat5(BiddingObservation.apply)

  final case class GameObservation(
    round_player: Option[PlayerDirection],
    round_cards: List[Core.Card],
    dummy_cards: List[Core.Card],
    tricks: Map[Core.PairDirection, List[Trick]],
    hand: List[Core.Card],
  )
  given RootJsonFormat[GameObservation] = jsonFormat5(GameObservation.apply)

  final case class GameState(
    base: BaseObservation,
    bidding: BiddingObservation,
    game: GameObservation,
  )
  given RootJsonFormat[GameState] = jsonFormat3(GameState.apply)

  object GameState {
    def apply(info: Session.SessionInfo): Option[GameState] =
      for
        observation <- info.playerObservation
      yield
        GameState(
          base = BaseObservation(
            game_stage = observation.gameStage,
            current_player = ???,
            user_direction = ???,
          ),
          bidding = BiddingObservation(
            first_dealer = observation.bidding.firstDealer,
            bid_history = observation.bidding.bidHistory,
            bid = observation.bidding.bid,
            declarer = observation.bidding.declarer,
            multiplier = observation.bidding.multiplier,
          ),
          game = GameObservation(
            round_player = observation.game.roundPlayer,
            round_cards = observation.game.roundCards,
            dummy_cards = observation.game.dummy,
            tricks = observation.game.tricks.mapValues(_.map(Trick.apply)).toMap,
            hand = observation.hand,
          ),
        )
  }
}

object HttpServer {

  import GameStateModels.given

  // --- / ---

  // POST /heartbeat
  // heartbeat the logged in user


  // --- /session ---

  // GET /session/info
  // get info about the session the logged in user is in

  private final case class PlayerModel(id: User.Id, ready: Boolean, position: Int)
  private given RootJsonFormat[PlayerModel] = jsonFormat3(PlayerModel.apply)

  private final case class GetSessionInfoResponse(sessionId: Session.Id, hostId: User.Id, users: List[PlayerModel], started: Boolean, gameState: Option[GameStateModels.GameState])
  private given RootJsonFormat[GetSessionInfoResponse] = jsonFormat5(GetSessionInfoResponse.apply)

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


  // --- /session/game ---

  // POST /session/game/play
  // play an action in the game the logged in user is in

  private final case class PlayActionRequest(suit: String, rank: String):
    def action: Core.Action =
      val suit_ = suit match
        case "clubs" => Core.Suit.Clubs
        case "diamonds" => Core.Suit.Diamonds
        case "hearts" => Core.Suit.Hearts
        case "spades" => Core.Suit.Spades
      val rank_ = rank match
        case "2" => Core.Rank.Two
        case "3" => Core.Rank.Three
        case "4" => Core.Rank.Four
        case "5" => Core.Rank.Five
        case "6" => Core.Rank.Six
        case "7" => Core.Rank.Seven
        case "8" => Core.Rank.Eight
        case "9" => Core.Rank.Nine
        case "10" => Core.Rank.Ten
        case "jack" => Core.Rank.Jack
        case "queen" => Core.Rank.Queen
        case "king" => Core.Rank.King
        case "ace" => Core.Rank.Ace
      Core.Play(Core.Card(suit_, rank_))
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
                          info.users map { user => PlayerModel(user.id, user.ready, user.position.ordinal) },
                          info.started,
                          GameStateModels.GameState(info),
                        )
                        ws.TextMessage(resp.toJson.compactPrint)
                      }.mapMaterializedValue(backend ! Backend.SubscribeToSessionInfo(sessionId, _))
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
