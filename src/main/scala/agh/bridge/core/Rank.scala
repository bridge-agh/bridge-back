package agh.bridge.core

import me.shadaj.scalapy.py.Dynamic

enum Rank:
  case Two, Three, Four, Five, Six, Seven, Eight, Nine, Ten, Jack, Queen, King, Ace

  def py = this match
    case Two => BridgePy.core.Rank.TWO
    case Three => BridgePy.core.Rank.THREE
    case Four => BridgePy.core.Rank.FOUR
    case Five => BridgePy.core.Rank.FIVE
    case Six => BridgePy.core.Rank.SIX
    case Seven => BridgePy.core.Rank.SEVEN
    case Eight => BridgePy.core.Rank.EIGHT
    case Nine => BridgePy.core.Rank.NINE
    case Ten => BridgePy.core.Rank.TEN
    case Jack => BridgePy.core.Rank.JACK
    case Queen => BridgePy.core.Rank.QUEEN
    case King => BridgePy.core.Rank.KING
    case Ace => BridgePy.core.Rank.ACE

object Rank:
  def apply(py: Dynamic): Rank = py.name.as[String] match
    case "TWO" => Rank.Two
    case "THREE" => Rank.Three
    case "FOUR" => Rank.Four
    case "FIVE" => Rank.Five
    case "SIX" => Rank.Six
    case "SEVEN" => Rank.Seven
    case "EIGHT" => Rank.Eight
    case "NINE" => Rank.Nine
    case "TEN" => Rank.Ten
    case "JACK" => Rank.Jack
    case "QUEEN" => Rank.Queen
    case "KING" => Rank.King
    case "ACE" => Rank.Ace
