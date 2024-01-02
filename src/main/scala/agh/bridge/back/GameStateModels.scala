package agh.bridge.back

import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

import agh.bridge.core as Core
import agh.bridge.core.PlayerDirection

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
    def write(direction: Core.PairDirection): JsValue = direction match
      case Core.PairDirection.NorthSouth => JsString("NS")
      case Core.PairDirection.EastWest => JsString("EW")
    def read(value: JsValue): Core.PairDirection = value match
      case JsString("NS") => Core.PairDirection.NorthSouth
      case JsString("EW") => Core.PairDirection.EastWest
      case _ => throw DeserializationException("Expected PairDirection")
  }

  given RootJsonFormat[Core.Bid] = new RootJsonFormat[Core.Bid] {
    def write(bid: Core.Bid): JsValue = JsObject(
      "suit" -> bid.suit.toJson,
      "tricks" -> bid.level.toJson,
    )
    def read(value: JsValue): Core.Bid = value match
      case obj: JsObject => Core.Bid(
        suit = obj.fields("suit").convertTo[Core.BidSuit],
        level = obj.fields("tricks").convertTo[Core.BidLevel],
      )
      case _ => throw DeserializationException("Expected Bid")
  }

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
    def apply(userId: User.Id, info: Session.SessionInfo): Option[GameState] =
      for
        observation <- info.playerObservation
      yield
        GameState(
          base = BaseObservation(
            game_stage = observation.gameStage,
            current_player = observation.currentPlayer,
            user_direction = info.users.find(_.id == userId).get.position,
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
