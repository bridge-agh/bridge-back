package agh.bridge.core

import me.shadaj.scalapy.py.Dynamic

enum PlayerDirection:
  case North, East, South, West

  def py = this match
    case North => BridgePy.core.PlayerDirection.NORTH
    case East => BridgePy.core.PlayerDirection.EAST
    case South => BridgePy.core.PlayerDirection.SOUTH
    case West => BridgePy.core.PlayerDirection.WEST

object PlayerDirection:
  def apply(py: Dynamic): PlayerDirection = py.name.as[String] match
    case "NORTH" => PlayerDirection.North
    case "EAST" => PlayerDirection.East
    case "SOUTH" => PlayerDirection.South
    case "WEST" => PlayerDirection.West

  def fromString(py: Dynamic): PlayerDirection = py.as[String] match
    case "N" => PlayerDirection.North
    case "E" => PlayerDirection.East
    case "S" => PlayerDirection.South
    case "W" => PlayerDirection.West

  def fromOptional(py: Dynamic): Option[PlayerDirection] =
    if Dynamic.global.isinstance(py, BridgePy.core.PlayerDirection).as[Boolean] then
      Some(PlayerDirection(py))
    else
      None
