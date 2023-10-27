package agh.bridge.core

import me.shadaj.scalapy.py as python

object BridgePy {
  val core = python.module("bridge_core_py.core")
  val bids = python.module("bridge_core_py.bids")
}
