package agh.bridge.core

class Game(seed: Long) {
  private val game = BridgePy.core.Game(seed)

  def step(action: Action) = {

  }
}
