package agh.bridge.core

import me.shadaj.scalapy.py as python

object BridgeCorePy {
  val core = python.module("bridge_core_py.core")
  val bids = python.module("bridge_core_py.bids")
}

enum PlayerDirection:
  case North, East, South, West

  def py = this match
    case North => BridgeCorePy.core.PlayerDirection.NORTH
    case East => BridgeCorePy.core.PlayerDirection.EAST
    case South => BridgeCorePy.core.PlayerDirection.SOUTH
    case West => BridgeCorePy.core.PlayerDirection.WEST

enum Suit:
  case Clubs, Diamonds, Hearts, Spades

  def py = this match
    case Clubs => BridgeCorePy.core.CardSuit.CLUBS
    case Diamonds => BridgeCorePy.core.CardSuit.DIAMONDS
    case Hearts => BridgeCorePy.core.CardSuit.HEARTS
    case Spades => BridgeCorePy.core.CardSuit.SPADES

enum Rank:
  case Two, Three, Four, Five, Six, Seven, Eight, Nine, Ten, Jack, Queen, King, Ace

  def py = this match
    case Two => BridgeCorePy.core.Rank.TWO
    case Three => BridgeCorePy.core.Rank.THREE
    case Four => BridgeCorePy.core.Rank.FOUR
    case Five => BridgeCorePy.core.Rank.FIVE
    case Six => BridgeCorePy.core.Rank.SIX
    case Seven => BridgeCorePy.core.Rank.SEVEN
    case Eight => BridgeCorePy.core.Rank.EIGHT
    case Nine => BridgeCorePy.core.Rank.NINE
    case Ten => BridgeCorePy.core.Rank.TEN
    case Jack => BridgeCorePy.core.Rank.JACK
    case Queen => BridgeCorePy.core.Rank.QUEEN
    case King => BridgeCorePy.core.Rank.KING
    case Ace => BridgeCorePy.core.Rank.ACE


final case class Card(suit: Suit, rank: Rank):
  def py = BridgeCorePy.core.Card(suit.py, rank.py)

enum BidLevel:
  case One, Two, Three, Four, Five, Six, Seven

  def py = this match
    case One => BridgeCorePy.bids.Tricks.ONE
    case Two => BridgeCorePy.bids.Tricks.TWO
    case Three => BridgeCorePy.bids.Tricks.THREE
    case Four => BridgeCorePy.bids.Tricks.FOUR
    case Five => BridgeCorePy.bids.Tricks.FIVE
    case Six => BridgeCorePy.bids.Tricks.SIX
    case Seven => BridgeCorePy.bids.Tricks.SEVEN

enum BidSuit:
  case Clubs, Diamonds, Hearts, Spades, NoTrump

  def py = this match
    case Clubs => BridgeCorePy.bids.Suit.CLUBS
    case Diamonds => BridgeCorePy.bids.Suit.DIAMONDS
    case Hearts => BridgeCorePy.bids.Suit.HEARTS
    case Spades => BridgeCorePy.bids.Suit.SPADES
    case NoTrump => BridgeCorePy.bids.Suit.NO_TRUMP

sealed trait Action:
  def py: python.Dynamic

sealed trait Call extends Action

final case class Bid(level: BidLevel, suit: BidSuit) extends Call:
  def py = BridgeCorePy.bids.TrickBid(suit.py, level.py)

case object Pass extends Call:
  def py = BridgeCorePy.bids.SpecialBid.PASS

case object Double extends Call:
  def py = BridgeCorePy.bids.SpecialBid.DOUBLE

case object Redouble extends Call:
  def py = BridgeCorePy.bids.SpecialBid.DOUBLE

final case class Play(card: Card) extends Action:
  def py = card.py

class Game(seed: Long) {
  private val game = BridgeCorePy.core.Game(seed)

  def step(action: Action) = {

  }
}
