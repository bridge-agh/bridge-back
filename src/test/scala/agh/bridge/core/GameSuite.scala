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
      assertEquals(Redouble.py.name.as[String], "REDOUBLE")
    }

    test("play py") {
      assertEquals(Play(Card(Suit.Diamonds, Rank.Ace)).py.suit.name.as[String], "DIAMONDS")
      assertEquals(Play(Card(Suit.Diamonds, Rank.Ace)).py.rank.name.as[String], "ACE")
    }

    test("create game") {
      val game = new Game(0)
    }

    test("observation after stepping") {
      val game = new Game(0)

      val actions = List(
        Bid(BidLevel.Three, BidSuit.Spades),
        Bid(BidLevel.Seven, BidSuit.Hearts),
        Pass,
        Bid(BidLevel.Seven, BidSuit.Spades),
        Bid(BidLevel.Seven, BidSuit.NoTrump),
        Double,
        Redouble,
        Pass,
        Pass,
        Pass,
        Play(Card(Suit.Diamonds, Rank.Eight)),
        Play(Card(Suit.Diamonds, Rank.Ace)),
        Play(Card(Suit.Diamonds, Rank.Three)),
        Play(Card(Suit.Diamonds, Rank.Queen)),
        Play(Card(Suit.Spades, Rank.Ten)),
        Play(Card(Suit.Spades, Rank.Four)),
        Play(Card(Suit.Hearts, Rank.Ten)),
        Play(Card(Suit.Spades, Rank.Six)),
        Play(Card(Suit.Hearts, Rank.Six)),
        Play(Card(Suit.Hearts, Rank.Five)),
        Play(Card(Suit.Hearts, Rank.Three))
      )

      actions.foreach(game.step)

      val player_obs = PlayerObservation(
        gameStage = GameStage.Playing,
        currentPlayer = PlayerDirection.East,
        bidding = PlayerObservation.Bidding(
          firstDealer = PlayerDirection.North,
          bidHistory = List(
            Bid(BidLevel.Three, BidSuit.Spades),
            Bid(BidLevel.Seven, BidSuit.Hearts),
            Pass,
            Bid(BidLevel.Seven, BidSuit.Spades),
            Bid(BidLevel.Seven, BidSuit.NoTrump),
            Double,
            Redouble,
            Pass,
            Pass,
            Pass
          ),
          bid = Some(Bid(BidLevel.Seven, BidSuit.NoTrump)),
          declarer = Some(PlayerDirection.North),
          multiplier = 4
        ),
        game = PlayerObservation.Game(
          roundPlayer = Some(PlayerDirection.South),
          roundCards = List(
            Card(Suit.Hearts, Rank.Six),
            Card(Suit.Hearts, Rank.Five),
            Card(Suit.Hearts, Rank.Three)
          ),
          dummy = List(
            Card(Suit.Clubs, Rank.Two),
            Card(Suit.Clubs, Rank.Ten),
            Card(Suit.Clubs, Rank.Jack),
            Card(Suit.Hearts, Rank.Two),
            Card(Suit.Hearts, Rank.Eight),
            Card(Suit.Hearts, Rank.King),
            Card(Suit.Spades, Rank.Five),
            Card(Suit.Spades, Rank.Nine),
            Card(Suit.Spades, Rank.Jack),
            Card(Suit.Spades, Rank.King)
          ),
          tricks = Map(
            PairDirection.NorthSouth -> List(
              (PlayerDirection.East, PlayerDirection.South, List(
                Card(Suit.Diamonds, Rank.Eight),
                Card(Suit.Diamonds, Rank.Ace),
                Card(Suit.Diamonds, Rank.Three),
                Card(Suit.Diamonds, Rank.Queen)
              )),
              (PlayerDirection.South, PlayerDirection.South, List(
                Card(Suit.Spades, Rank.Ten),
                Card(Suit.Spades, Rank.Four),
                Card(Suit.Hearts, Rank.Ten),
                Card(Suit.Spades, Rank.Six)
              ))
            ),
            PairDirection.EastWest -> List()
          )
        ),
        hand = List(
          Card(Suit.Clubs, Rank.Three),
          Card(Suit.Clubs, Rank.Four),
          Card(Suit.Clubs, Rank.Five),
          Card(Suit.Clubs, Rank.Eight),
          Card(Suit.Diamonds, Rank.Five),
          Card(Suit.Diamonds, Rank.Six),
          Card(Suit.Diamonds, Rank.Nine),
          Card(Suit.Diamonds, Rank.Jack),
          Card(Suit.Spades, Rank.Three),
          Card(Suit.Spades, Rank.Eight),
          Card(Suit.Spades, Rank.Queen)
        )
      )
      assertEquals(game.playerObservation(game.currentPlayer), player_obs)
    }
}
