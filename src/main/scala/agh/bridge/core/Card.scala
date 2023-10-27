package agh.bridge.core

import me.shadaj.scalapy.py.Dynamic

final case class Card(suit: Suit, rank: Rank):
  def py = BridgePy.core.Card(suit.py, rank.py)

object Card:
  def apply(py: Dynamic): Card =
    Card(Suit(py.suit), Rank(py.rank))
