package agh.bridge.back

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.HttpMethods._

object CorsHandler {
  private val corsResponseHeaders = List(
    `Access-Control-Allow-Origin`.*,
    `Access-Control-Allow-Credentials`(true),
    `Access-Control-Allow-Headers`("Authorization", "Content-Type", "X-Requested-With")
  )

  private def addAccessControlHeaders: Directive0 = {
    respondWithHeaders(corsResponseHeaders)
  }

  private def preflightRequestHandler: Route = options {
    complete(HttpResponse(StatusCodes.OK).
      withHeaders(`Access-Control-Allow-Methods`(OPTIONS, POST, PUT, GET, DELETE)))
  }

  def cors(r: Route): Route = addAccessControlHeaders {
    concat(preflightRequestHandler, r)
  }
}
