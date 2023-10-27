package agh.bridge.core

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
