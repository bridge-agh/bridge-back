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
      infoOpt <- backend.ask[Either[Backend.SessionNotFound, Session.LobbyInfo]](Backend.GetLobbyInfo(sessionId, _))
    yield
      assertEquals(infoOpt, Right(Session.LobbyInfo("host", List(Session.Player("host", false, 0)), false)))
  }

  backendTestKit.test("find session") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("host", _))
      foundSessionIdOpt <- backend.ask[Either[Backend.SessionNotFound, Session.Id]](Backend.FindSession("host", _))
    yield
      assertEquals(foundSessionIdOpt, Right(sessionId))
  }

  backendTestKit.test("join lobby") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("host", _))
      joinResult <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby(sessionId, "guest", _))
      infoOpt <- backend.ask[Either[Backend.SessionNotFound, Session.LobbyInfo]](Backend.GetLobbyInfo(sessionId, _))
    yield
      assertEquals(joinResult, Right(()))
      assertEquals(infoOpt, Right(Session.LobbyInfo("host", List(Session.Player("host", false, 0), Session.Player("guest", false, 1)), false)))
  }

  backendTestKit.test("leave lobby") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("host", _))
      joinRes <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby(sessionId, "guest", _))
      leaveRes <- backend.ask[Unit](Backend.LeaveLobby("guest", _))
      infoOpt <- backend.ask[Either[Backend.SessionNotFound, Session.LobbyInfo]](Backend.GetLobbyInfo(sessionId, _))
    yield
      assert(sessionId.nonEmpty)
      assertEquals(joinRes, Right(()))
      assertEquals(leaveRes, ())
      assertEquals(infoOpt, Right(Session.LobbyInfo("host", List(Session.Player("host", false, 0)), false)))
  }

  backendTestKit.test("set ready") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("host", _))
      joinRes <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby(sessionId, "guest", _))
      readyRes1 <- backend.ask[Either[User.UserNotInSession, Unit]](Backend.SetUserReady("guest", true, _))
      infoOpt1 <- backend.ask[Either[Backend.SessionNotFound, Session.LobbyInfo]](Backend.GetLobbyInfo(sessionId, _))
      readyRes2 <- backend.ask[Either[User.UserNotInSession, Unit]](Backend.SetUserReady("host", true, _))
      infoOpt2 <- backend.ask[Either[Backend.SessionNotFound, Session.LobbyInfo]](Backend.GetLobbyInfo(sessionId, _))
      readyRes3 <- backend.ask[Either[User.UserNotInSession, Unit]](Backend.SetUserReady("guest", false, _))
      infoOpt3 <- backend.ask[Either[Backend.SessionNotFound, Session.LobbyInfo]](Backend.GetLobbyInfo(sessionId, _))
    yield
      assertEquals(joinRes, Right(()))
      assertEquals(readyRes1, Right(()))
      assertEquals(infoOpt1, Right(Session.LobbyInfo("host", List(Session.Player("host", false, 0), Session.Player("guest", true, 1)), false)))
      assertEquals(readyRes2, Right(()))
      assertEquals(infoOpt2, Right(Session.LobbyInfo("host", List(Session.Player("host", true, 0), Session.Player("guest", true, 1)), false)))
      assertEquals(readyRes3, Right(()))
      assertEquals(infoOpt3, Right(Session.LobbyInfo("host", List(Session.Player("host", true, 0), Session.Player("guest", false, 1)), false)))
  }

  backendTestKit.test("create lobby when in lobby") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId1 <- backend.ask[Session.Id](Backend.CreateLobby("host", _))
      sessionId2 <- backend.ask[Session.Id](Backend.CreateLobby("host", _))
      foundSessionId <- backend.ask[Either[Backend.SessionNotFound, Session.Id]](Backend.FindSession("host", _))
      infoOpt1 <- backend.ask[Either[Backend.SessionNotFound, Session.LobbyInfo]](Backend.GetLobbyInfo(sessionId1, _))
      infoOpt2 <- backend.ask[Either[Backend.SessionNotFound, Session.LobbyInfo]](Backend.GetLobbyInfo(sessionId2, _))
    yield
      assertEquals(foundSessionId, Right(sessionId2))
      assertEquals(infoOpt1, Left(Backend.SessionNotFound))
      assertEquals(infoOpt2, Right(Session.LobbyInfo("host", List(Session.Player("host", false, 0)), false)))
  }

  backendTestKit.test("join lobby when in lobby") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId1 <- backend.ask[Session.Id](Backend.CreateLobby("user1", _))
      sessionId2 <- backend.ask[Session.Id](Backend.CreateLobby("user2", _))
      joinRes <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby(sessionId2, "user1", _))
      foundSessionId <- backend.ask[Either[Backend.SessionNotFound, Session.Id]](Backend.FindSession("user1", _))
      infoOpt1 <- backend.ask[Either[Backend.SessionNotFound, Session.LobbyInfo]](Backend.GetLobbyInfo(sessionId1, _))
      infoOpt2 <- backend.ask[Either[Backend.SessionNotFound, Session.LobbyInfo]](Backend.GetLobbyInfo(sessionId2, _))
    yield
      assertEquals(foundSessionId, Right(sessionId2))
      assertEquals(infoOpt1, Left(Backend.SessionNotFound))
      assertEquals(infoOpt2, Right(Session.LobbyInfo("user2", List(Session.Player("user2", false, 0), Session.Player("user1", false, 1)), false)))
  }

  backendTestKit.test("session deletes when all leave") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("host", _))
      leaveRes <- backend.ask[Unit](Backend.LeaveLobby("host", _))
      foundSessionId <- backend.ask[Either[Backend.SessionNotFound, Session.Id]](Backend.FindSession("host", _))
      infoOpt <- backend.ask[Either[Backend.SessionNotFound, Session.LobbyInfo]](Backend.GetLobbyInfo(sessionId, _))
    yield
      assertEquals(foundSessionId, Left(Backend.SessionNotFound))
      assertEquals(infoOpt, Left(Backend.SessionNotFound))
  }
}
