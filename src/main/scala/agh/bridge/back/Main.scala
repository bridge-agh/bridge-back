package agh.bridge.back

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.actor.typed.scaladsl.AskPattern._

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

object MainSystem {
  def apply(): Behavior[Nothing] = Behaviors.setup { context =>
    val backend = context.spawn(Backend(), "backend")
    val httpServer = context.spawn(HttpServer(backend), "http-server")
    Behaviors.empty
  }
}

@main def run: Unit =
  val system = ActorSystem(MainSystem(), "main-system")
