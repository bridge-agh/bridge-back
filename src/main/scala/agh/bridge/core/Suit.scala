package agh.bridge.core

import me.shadaj.scalapy.py.Dynamic

enum Suit:
  case Clubs, Diamonds, Hearts, Spades

  def py = this match
    case Clubs => BridgePy.core.CardSuit.CLUBS
    case Diamonds => BridgePy.core.CardSuit.DIAMONDS
    case Hearts => BridgePy.core.CardSuit.HEARTS
    case Spades => BridgePy.core.CardSuit.SPADES

object Suit:
  def apply(py: Dynamic): Suit = py.name.as[String] match
    case "CLUBS" => Suit.Clubs
    case "DIAMONDS" => Suit.Diamonds
    case "HEARTS" => Suit.Hearts
    case "SPADES" => Suit.Spades
