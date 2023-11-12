package agh.bridge.back

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.directives.Credentials

import agh.bridge.firebase.Authentication


object AuthHandler:
  private def authenticator(credentials: Credentials): Option[String] =
    credentials match
      case Credentials.Provided(token) =>
        Authentication.verifyIdToken(token)
      case _ => None

  def requireAuthentication: Directive1[String] =
    authenticateOAuth2("bridge-agh", authenticator)
