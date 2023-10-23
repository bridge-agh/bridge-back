import scala.concurrent.ExecutionContext

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.actor.typed.scaladsl.AskPattern._

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

object HttpServer {
  def apply(): Behavior[Nothing] = Behaviors.setup { context =>
    given ActorSystem[_] = context.system
    given ExecutionContext = context.executionContext
    given akka.util.Timeout = akka.util.Timeout(1000, java.util.concurrent.TimeUnit.MILLISECONDS)

    val route =
      concat(
        pathPrefix("session") {
          concat(
            path("heartbeat") {
              post {
                complete("ok")
              }
            },
            path("find") {
              get {
                complete("ok")
              }
            },
            pathPrefix("lobby") {
              concat(
                path("create") {
                  post {
                    complete("ok")
                  }
                },
                path("join") {
                  post {
                    complete("ok")
                  }
                },
                path("leave") {
                  post {
                    complete("ok")
                  }
                },
                path("info") {
                  post {
                    complete("ok")
                  }
                },
                path("ready") {
                  post {
                    complete("ok")
                  }
                },
              )
            },
            pathPrefix("game") {
              concat(
                path("info") {
                  post {
                    complete("ok")
                  }
                },
                path("play") {
                  post {
                    complete("ok")
                  }
                },
                path("pass") {
                  post {
                    complete("ok")
                  }
                },
                path("double") {
                  post {
                    complete("ok")
                  }
                },
                path("bid") {
                  post {
                    complete("ok")
                  }
                },
              )
            },
          )
        },
      )

    Http().newServerAt("localhost", 8000).bind(route)

    Behaviors.empty
  }
}

object MainSystem {
  def apply(): Behavior[Nothing] = Behaviors.setup { context =>
    val httpServer = context.spawn(HttpServer(), "http-server")
    Behaviors.empty
  }
}

@main def run: Unit =
  val system = ActorSystem(MainSystem(), "main-system")
