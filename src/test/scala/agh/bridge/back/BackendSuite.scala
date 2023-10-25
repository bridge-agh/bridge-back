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
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("host", _))
      infoOpt <- backend.ask[Option[Session.LobbyInfo]](Backend.GetLobbyInfo(sessionId, _))
    yield
      assertNotEquals(sessionId.length, 0)
      assertEquals(infoOpt, Some(Session.LobbyInfo("host", List(Session.Player("host", false, 0)), false)))
  }

  backendTestKit.test("find session") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("host", _))
      foundSessionIdOpt <- backend.ask[Option[Session.Id]](Backend.FindSession("host", _))
    yield
      assertEquals(foundSessionIdOpt, Some(sessionId))
  }

  backendTestKit.test("join lobby") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("host", _))
      joinResult <- backend.ask[Either[Backend.JoinLobbyError, Unit]](Backend.JoinLobby(sessionId, "guest", _))
      infoOpt <- backend.ask[Option[Session.LobbyInfo]](Backend.GetLobbyInfo(sessionId, _))
    yield
      assertEquals(joinResult, Right(()))
      assertEquals(infoOpt, Some(Session.LobbyInfo("host", List(Session.Player("host", false, 0), Session.Player("guest", false, 1)), false)))
  }

  backendTestKit.test("leave lobby") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    val sessionIdFut = backend.ask[Session.Id](Backend.CreateLobby("host", _))
    for
      sessionId <- sessionIdFut
      joinResult <- backend.ask[Either[Backend.JoinLobbyError, Unit]](Backend.JoinLobby(sessionId, "guest", _))
    yield
      assertEquals(joinResult, Right(()))
      backend ! Backend.LeaveLobby("guest")
      Thread.sleep(50)
      for
        infoOpt <- backend.ask[Option[Session.LobbyInfo]](Backend.GetLobbyInfo(sessionId, _))
      yield
        assertEquals(infoOpt, Some(Session.LobbyInfo("host", List(Session.Player("host", false, 0)), false)))
  }
}
