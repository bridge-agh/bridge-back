package agh.bridge.core

import me.shadaj.scalapy.py.Dynamic

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

object BidLevel:
  def apply(py: Dynamic): BidLevel =
    py.name.as[String] match
      case "ONE" => BidLevel.One
      case "TWO" => BidLevel.Two
      case "THREE" => BidLevel.Three
      case "FOUR" => BidLevel.Four
      case "FIVE" => BidLevel.Five
      case "SIX" => BidLevel.Six
      case "SEVEN" => BidLevel.Seven
