package agh.bridge.back

import scala.concurrent.Await

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorSystem, Behavior }

import org.slf4j.LoggerFactory

object MainSystem {
  def apply(): Behavior[Nothing] = Behaviors.setup { context =>
    val backend = context.spawn(Backend(), "backend")
    val httpServer = context.spawn(HttpServer(backend), "http-server")
    Behaviors.empty
  }
}

@main def run: Unit =
  val log = LoggerFactory.getLogger("agh.bridge.back.run")
  log.info("Starting main system")
  val system = ActorSystem(MainSystem(), "main-system")
  Await.result(system.whenTerminated, scala.concurrent.duration.Duration.Inf)
  log.info("Main system terminated")
