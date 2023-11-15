package agh.bridge.core

import me.shadaj.scalapy.py.Dynamic

sealed trait Action:
  def py: Dynamic

sealed trait Call extends Action

object Call:
  def apply(py: Dynamic): Call =
    if Dynamic.global.isinstance(py, BridgePy.bids.TrickBid).as[Boolean] then
      Bid(py)
    else
      py.name.as[String] match
        case "PASS" => Pass
        case "DOUBLE" => Double 

final case class Bid(level: BidLevel, suit: BidSuit) extends Call:
  def py = BridgePy.bids.TrickBid(suit.py, level.py)

object Bid:
  def apply(py: Dynamic): Bid =
    Bid(BidLevel(py.tricks), BidSuit(py.suit))

  def fromOptional(py: Dynamic): Option[Bid] =
    if Dynamic.global.isinstance(py, BridgePy.bids.TrickBid).as[Boolean] then
      Some(Bid(BidLevel(py.tricks), BidSuit(py.suit)))
    else
      None

case object Pass extends Call:
  def py = BridgePy.bids.SpecialBid.PASS

case object Double extends Call:
  def py = BridgePy.bids.SpecialBid.DOUBLE

case object Redouble extends Call:
  def py = BridgePy.bids.SpecialBid.DOUBLE

final case class Play(card: Card) extends Action:
  def py = card.py
