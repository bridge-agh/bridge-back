package agh.bridge.core

import me.shadaj.scalapy.py.Dynamic

enum BidSuit:
  case Clubs, Diamonds, Hearts, Spades, NoTrump

  def py = this match
    case Clubs => BridgePy.bids.Suit.CLUBS
    case Diamonds => BridgePy.bids.Suit.DIAMONDS
    case Hearts => BridgePy.bids.Suit.HEARTS
    case Spades => BridgePy.bids.Suit.SPADES
    case NoTrump => BridgePy.bids.Suit.NO_TRUMP

object BidSuit:
  def apply(py: Dynamic): BidSuit =
    py.name.as[String] match
      case "CLUBS" => BidSuit.Clubs
      case "DIAMONDS" => BidSuit.Diamonds
      case "HEARTS" => BidSuit.Hearts
      case "SPADES" => BidSuit.Spades
      case "NO_TRUMP" => BidSuit.NoTrump
