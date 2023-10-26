package agh.bridge.core

import munit.FunSuite

class GameSuite extends FunSuite {
    test("direction py") {
      assertEquals(PlayerDirection.North.py.name.as[String], "NORTH")
      assertEquals(PlayerDirection.East.py.name.as[String], "EAST")
      assertEquals(PlayerDirection.South.py.name.as[String], "SOUTH")
      assertEquals(PlayerDirection.West.py.name.as[String], "WEST")
    }

    test("suit py") {
      assertEquals(Suit.Clubs.py.name.as[String], "CLUBS")
      assertEquals(Suit.Diamonds.py.name.as[String], "DIAMONDS")
      assertEquals(Suit.Hearts.py.name.as[String], "HEARTS")
      assertEquals(Suit.Spades.py.name.as[String], "SPADES")
    }

    test("rank py") {
      assertEquals(Rank.Two.py.name.as[String], "TWO")
      assertEquals(Rank.Three.py.name.as[String], "THREE")
      assertEquals(Rank.Four.py.name.as[String], "FOUR")
      assertEquals(Rank.Five.py.name.as[String], "FIVE")
      assertEquals(Rank.Six.py.name.as[String], "SIX")
      assertEquals(Rank.Seven.py.name.as[String], "SEVEN")
      assertEquals(Rank.Eight.py.name.as[String], "EIGHT")
      assertEquals(Rank.Nine.py.name.as[String], "NINE")
      assertEquals(Rank.Ten.py.name.as[String], "TEN")
      assertEquals(Rank.Jack.py.name.as[String], "JACK")
      assertEquals(Rank.Queen.py.name.as[String], "QUEEN")
      assertEquals(Rank.King.py.name.as[String], "KING")
      assertEquals(Rank.Ace.py.name.as[String], "ACE")
    }

    test("card py") {
      assertEquals(Card(Suit.Diamonds, Rank.Ace).py.suit.name.as[String], "DIAMONDS")
      assertEquals(Card(Suit.Diamonds, Rank.Ace).py.rank.name.as[String], "ACE")
    }

    test("bid level py") {
      assertEquals(BidLevel.One.py.name.as[String], "ONE")
      assertEquals(BidLevel.Two.py.name.as[String], "TWO")
      assertEquals(BidLevel.Three.py.name.as[String], "THREE")
      assertEquals(BidLevel.Four.py.name.as[String], "FOUR")
      assertEquals(BidLevel.Five.py.name.as[String], "FIVE")
      assertEquals(BidLevel.Six.py.name.as[String], "SIX")
      assertEquals(BidLevel.Seven.py.name.as[String], "SEVEN")
    }

    test("bid suit py") {
      assertEquals(BidSuit.Clubs.py.name.as[String], "CLUBS")
      assertEquals(BidSuit.Diamonds.py.name.as[String], "DIAMONDS")
      assertEquals(BidSuit.Hearts.py.name.as[String], "HEARTS")
      assertEquals(BidSuit.Spades.py.name.as[String], "SPADES")
      assertEquals(BidSuit.NoTrump.py.name.as[String], "NO_TRUMP")
    }

    test("bid py") {
      assertEquals(Bid(BidLevel.Five, BidSuit.NoTrump).py.suit.name.as[String], "NO_TRUMP")
      assertEquals(Bid(BidLevel.Five, BidSuit.NoTrump).py.tricks.name.as[String], "FIVE")
    }

    test("special calls py") {
      assertEquals(Pass.py.name.as[String], "PASS")
      assertEquals(Double.py.name.as[String], "DOUBLE")
      assertEquals(Redouble.py.name.as[String], "DOUBLE")
    }

    test("play py") {
      assertEquals(Play(Card(Suit.Diamonds, Rank.Ace)).py.suit.name.as[String], "DIAMONDS")
      assertEquals(Play(Card(Suit.Diamonds, Rank.Ace)).py.rank.name.as[String], "ACE")
    }

    test("create game") {
      val game = new Game(0)
    }
}
