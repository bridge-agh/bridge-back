package agh.bridge.core

enum BidLevel:
  case One, Two, Three, Four, Five, Six, Seven

  def py = this match
    case One => BridgePy.bids.Tricks.ONE
    case Two => BridgePy.bids.Tricks.TWO
    case Three => BridgePy.bids.Tricks.THREE
    case Four => BridgePy.bids.Tricks.FOUR
    case Five => BridgePy.bids.Tricks.FIVE
    case Six => BridgePy.bids.Tricks.SIX
    case Seven => BridgePy.bids.Tricks.SEVEN
