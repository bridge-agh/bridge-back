package agh.bridge.core

import me.shadaj.scalapy.py.Dynamic

enum GameStage:
  case Bidding, Playing, Scoring

object GameStage:
  def apply(py: Dynamic): GameStage = py.name.as[String] match
    case "BIDDING" => GameStage.Bidding
    case "PLAYING" => GameStage.Playing
    case "SCORING" => GameStage.Scoring

enum PairDirection:
  case NorthSouth, EastWest

object PairDirection:
  def fromString(py: Dynamic): PairDirection = py.as[String] match
    case "NS" => PairDirection.NorthSouth
    case "EW" => PairDirection.EastWest

final case class PlayerObservation(
  gameStage: GameStage,
  currentPlayer: PlayerDirection,
  bidding: PlayerObservation.Bidding,
  game: PlayerObservation.Game,
  hand: List[Card],
)

object PlayerObservation:
    def apply(py: Dynamic): PlayerObservation =
      val dict = py.as[Map[String, Dynamic]]
      PlayerObservation(
        gameStage = GameStage(dict("game_stage")),
        currentPlayer = PlayerDirection(dict("current_player")),
        bidding = PlayerObservation.Bidding(dict("bidding")),
        game = PlayerObservation.Game(dict("game")),
        hand = dict("hand").as[List[Dynamic]].map(Card(_)),
      )

    final case class Bidding(
      firstDealer: PlayerDirection,
      bidHistory: List[Call],
      bid: Bid,
      declarer: PlayerDirection,
      multiplier: Int,
    )

    object Bidding:
        def apply(py: Dynamic): Bidding =
          val dict = py.as[Map[String, Dynamic]]
          Bidding(
            firstDealer = PlayerDirection(dict("first_dealer")),
            bidHistory = dict("bid_history").as[List[Dynamic]].map(Call(_)),
            bid = Bid(dict("bid")),
            declarer = PlayerDirection(dict("declarer")),
            multiplier = dict("multiplier").as[Int],
          )

    final case class Game(
      roundPlayer: PlayerDirection,
      roundCards: List[Card],
      dummy: List[Card],
      tricks: Map[PairDirection, List[(PlayerDirection, PlayerDirection, List[Card])]],
    )

    object Game:
        def apply(py: Dynamic): Game =
          val dict = py.as[Map[String, Dynamic]]
          Game(
            roundPlayer = PlayerDirection(dict("round_player")),
            roundCards = dict("round_cards").as[List[Dynamic]].map(Card(_)),
            dummy = dict("dummy").as[List[Dynamic]].map(Card(_)),
            tricks = {
              dict("tricks").as[Map[Dynamic, Dynamic]].map((k, v) => (PairDirection.fromString(k), v.as[List[(Dynamic, Dynamic, List[Dynamic])]].map((k, v, w) => (PlayerDirection(k), PlayerDirection(v), w.map(Card(_))))))
            }
          )
