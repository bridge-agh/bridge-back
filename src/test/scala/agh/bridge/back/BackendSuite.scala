package agh.bridge.back

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import munit.FunSuite
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.actor.typed.scaladsl.AskPattern._

class BackendSuite extends FunSuite {
  given ExecutionContext = scala.concurrent.ExecutionContext.global
  given akka.util.Timeout = akka.util.Timeout(1000, java.util.concurrent.TimeUnit.MILLISECONDS)

  val backendTestKit = FunFixture(
      _ => {
        val testKit = ActorTestKit()
        val backend = testKit.spawn(Backend())
        (testKit, backend)
      },
      { (testKit, backend) =>
        testKit.shutdownTestKit()
      }
  )

  backendTestKit.test("create lobby") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    val hostId = "host"
    val sessionIdFut = backend.ask[Session.Id](Backend.CreateLobby(hostId, _))
    for 
      sessionId <- sessionIdFut
    yield
      assertNotEquals(sessionId.length, 0)
  }

  backendTestKit.test("find session") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    val hostId = "host"
    val sessionIdFut = backend.ask[Session.Id](Backend.CreateLobby(hostId, _))
    for
      sessionId <- sessionIdFut
      foundSessionId <- backend.ask[Option[Session.Id]](Backend.FindSession(hostId, _))
    yield
      assertEquals(foundSessionId, Some(sessionId))
  }

  backendTestKit.test("join lobby") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    val hostId = "host"
    val sessionIdFut = backend.ask[Session.Id](Backend.CreateLobby(hostId, _))
    for
      sessionId <- sessionIdFut
      joinResult <- backend.ask[Either[Backend.JoinLobbyError, Unit]](Backend.JoinLobby(sessionId, "guest", _))
    yield
      assertEquals(joinResult, Right(()))
  }
}
