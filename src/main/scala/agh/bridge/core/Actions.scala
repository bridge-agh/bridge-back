package agh.bridge.core

import me.shadaj.scalapy.py as python

sealed trait Action:
  def py: python.Dynamic

sealed trait Call extends Action

final case class Bid(level: BidLevel, suit: BidSuit) extends Call:
  def py = BridgePy.bids.TrickBid(suit.py, level.py)

case object Pass extends Call:
  def py = BridgePy.bids.SpecialBid.PASS

case object Double extends Call:
  def py = BridgePy.bids.SpecialBid.DOUBLE

case object Redouble extends Call:
  def py = BridgePy.bids.SpecialBid.DOUBLE

final case class Play(card: Card) extends Action:
  def py = card.py

