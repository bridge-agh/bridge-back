package agh.bridge.core

final case class Card(suit: Suit, rank: Rank):
  def py = BridgePy.core.Card(suit.py, rank.py)
