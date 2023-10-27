package agh.bridge.core

enum PlayerDirection:
  case North, East, South, West

  def py = this match
    case North => BridgePy.core.PlayerDirection.NORTH
    case East => BridgePy.core.PlayerDirection.EAST
    case South => BridgePy.core.PlayerDirection.SOUTH
    case West => BridgePy.core.PlayerDirection.WEST
