package agh.bridge.back

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorSystem, Behavior }

object MainSystem {
  def apply(): Behavior[Nothing] = Behaviors.setup { context =>
    val backend = context.spawn(Backend(), "backend")
    val httpServer = context.spawn(HttpServer(backend), "http-server")
    Behaviors.empty
  }
}

@main def run: Unit =
  val system = ActorSystem(MainSystem(), "main-system")
