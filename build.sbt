val scala3Version = "3.3.1"
val akkaVersion = "2.8.5"
val akkaHttpVersion = "10.5.3"

import ai.kien.python.Python
import sys.process._
lazy val python = Python("which python".!!.trim)
lazy val javaOpts = python.scalapyProperties.get.map {
  case (k, v) => s"""-D$k=$v"""
}.toSeq

lazy val root = project
  .in(file("."))
  .settings(
    name := "agh.bridge",
    version := "0.1.0",

    scalaVersion := scala3Version,
    fork := true,
    javaOptions ++= javaOpts,
    resolvers += "Akka library repository".at("https://repo.akka.io/maven"),

    libraryDependencies ++= Seq(
      "org.scalameta"     %% "munit"                    % "0.7.29"        % Test,
      "com.typesafe.akka" %% "akka-http-testkit"        % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion     % Test,
    ),

    libraryDependencies ++= Seq(      
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,

      "ch.qos.logback"    % "logback-classic"           % "1.2.12",

      "me.shadaj" %% "scalapy-core" % "0.5.2",
    ),
  )
