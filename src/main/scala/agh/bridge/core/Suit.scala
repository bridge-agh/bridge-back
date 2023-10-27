package agh.bridge.core

enum Suit:
  case Clubs, Diamonds, Hearts, Spades

  def py = this match
    case Clubs => BridgePy.core.CardSuit.CLUBS
    case Diamonds => BridgePy.core.CardSuit.DIAMONDS
    case Hearts => BridgePy.core.CardSuit.HEARTS
    case Spades => BridgePy.core.CardSuit.SPADES
