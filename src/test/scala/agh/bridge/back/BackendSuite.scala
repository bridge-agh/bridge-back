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
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("host", _))
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

  backendTestKit.test("set ready") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("host", _))
      joinRes <- backend.ask[Either[Backend.JoinLobbyError, Unit]](Backend.JoinLobby(sessionId, "guest", _))
      readyRes1 <- backend.ask[Either[User.SetReadyError, Unit]](Backend.SetUserReady("guest", true, _))
      infoOpt1 <- backend.ask[Option[Session.LobbyInfo]](Backend.GetLobbyInfo(sessionId, _))
      readyRes2 <- backend.ask[Either[User.SetReadyError, Unit]](Backend.SetUserReady("host", true, _))
      infoOpt2 <- backend.ask[Option[Session.LobbyInfo]](Backend.GetLobbyInfo(sessionId, _))
      readyRes3 <- backend.ask[Either[User.SetReadyError, Unit]](Backend.SetUserReady("guest", false, _))
      infoOpt3 <- backend.ask[Option[Session.LobbyInfo]](Backend.GetLobbyInfo(sessionId, _))
    yield
      assertEquals(joinRes, Right(()))
      assertEquals(readyRes1, Right(()))
      assertEquals(infoOpt1, Some(Session.LobbyInfo("host", List(Session.Player("host", false, 0), Session.Player("guest", true, 1)), false)))
      assertEquals(readyRes2, Right(()))
      assertEquals(infoOpt2, Some(Session.LobbyInfo("host", List(Session.Player("host", true, 0), Session.Player("guest", true, 1)), false)))
      assertEquals(readyRes3, Right(()))
      assertEquals(infoOpt3, Some(Session.LobbyInfo("host", List(Session.Player("host", true, 0), Session.Player("guest", false, 1)), false)))
  }
}
