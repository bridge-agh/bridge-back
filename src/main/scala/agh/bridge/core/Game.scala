package agh.bridge.core

import me.shadaj.scalapy.py as python

class Game(seed: Long) {
  private val game = BridgePy.core.Game(seed)

  def step(action: Action) = game.step(action.py)

  def playerObservation(player: PlayerDirection): PlayerObservation =
    PlayerObservation(game.player_observation(player.py))

  def currentPlayer: PlayerDirection = PlayerDirection(game.current_player)
}
