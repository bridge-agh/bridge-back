package agh.bridge.core

enum BidSuit:
  case Clubs, Diamonds, Hearts, Spades, NoTrump

  def py = this match
    case Clubs => BridgePy.bids.Suit.CLUBS
    case Diamonds => BridgePy.bids.Suit.DIAMONDS
    case Hearts => BridgePy.bids.Suit.HEARTS
    case Spades => BridgePy.bids.Suit.SPADES
    case NoTrump => BridgePy.bids.Suit.NO_TRUMP
