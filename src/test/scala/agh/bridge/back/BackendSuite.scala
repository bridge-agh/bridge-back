package agh.bridge.back

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import munit.FunSuite
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.actor.typed.scaladsl.AskPattern._

import agh.bridge.core.PlayerDirection

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
      infoOpt <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo(sessionId, _))
    yield
      assertEquals(infoOpt, Right(Session.SessionInfo("host", List(Session.PlayerInfo("host", false, PlayerDirection.North)), false)))
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
      infoOpt <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo(sessionId, _))
    yield
      assertEquals(joinResult, Right(()))
      assertEquals(infoOpt, Right(Session.SessionInfo("host", List(Session.PlayerInfo("host", false, PlayerDirection.North), Session.PlayerInfo("guest", false, PlayerDirection.East)), false)))
  }

  backendTestKit.test("leave lobby") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("host", _))
      joinRes <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby(sessionId, "guest", _))
      leaveRes <- backend.ask[Unit](Backend.LeaveLobby("guest", _))
      infoOpt <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo(sessionId, _))
    yield
      assert(sessionId.nonEmpty)
      assertEquals(joinRes, Right(()))
      assertEquals(leaveRes, ())
      assertEquals(infoOpt, Right(Session.SessionInfo("host", List(Session.PlayerInfo("host", false, PlayerDirection.North)), false)))
  }

  backendTestKit.test("set ready") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("host", _))
      joinRes <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby(sessionId, "guest", _))
      readyRes1 <- backend.ask[Either[Backend.UserNotInSession, Unit]](Backend.SetUserReady("guest", true, _))
      infoOpt1 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo(sessionId, _))
      readyRes2 <- backend.ask[Either[Backend.UserNotInSession, Unit]](Backend.SetUserReady("host", true, _))
      infoOpt2 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo(sessionId, _))
      readyRes3 <- backend.ask[Either[Backend.UserNotInSession, Unit]](Backend.SetUserReady("guest", false, _))
      infoOpt3 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo(sessionId, _))
    yield
      assertEquals(joinRes, Right(()))
      assertEquals(readyRes1, Right(()))
      assertEquals(infoOpt1, Right(Session.SessionInfo("host", List(Session.PlayerInfo("host", false, PlayerDirection.North), Session.PlayerInfo("guest", true, PlayerDirection.East)), false)))
      assertEquals(readyRes2, Right(()))
      assertEquals(infoOpt2, Right(Session.SessionInfo("host", List(Session.PlayerInfo("host", true, PlayerDirection.North), Session.PlayerInfo("guest", true, PlayerDirection.East)), false)))
      assertEquals(readyRes3, Right(()))
      assertEquals(infoOpt3, Right(Session.SessionInfo("host", List(Session.PlayerInfo("host", true, PlayerDirection.North), Session.PlayerInfo("guest", false, PlayerDirection.East)), false)))
  }

  backendTestKit.test("create lobby when in lobby") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId1 <- backend.ask[Session.Id](Backend.CreateLobby("host", _))
      sessionId2 <- backend.ask[Session.Id](Backend.CreateLobby("host", _))
      foundSessionId <- backend.ask[Either[Backend.SessionNotFound, Session.Id]](Backend.FindSession("host", _))
      infoOpt1 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo(sessionId1, _))
      infoOpt2 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo(sessionId2, _))
    yield
      assertEquals(foundSessionId, Right(sessionId2))
      assertEquals(infoOpt1, Left(Backend.SessionNotFound))
      assertEquals(infoOpt2, Right(Session.SessionInfo("host", List(Session.PlayerInfo("host", false, PlayerDirection.North)), false)))
  }

  backendTestKit.test("join lobby when in lobby") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId1 <- backend.ask[Session.Id](Backend.CreateLobby("user1", _))
      sessionId2 <- backend.ask[Session.Id](Backend.CreateLobby("user2", _))
      joinRes <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby(sessionId2, "user1", _))
      foundSessionId <- backend.ask[Either[Backend.SessionNotFound, Session.Id]](Backend.FindSession("user1", _))
      infoOpt1 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo(sessionId1, _))
      infoOpt2 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo(sessionId2, _))
    yield
      assertEquals(foundSessionId, Right(sessionId2))
      assertEquals(infoOpt1, Left(Backend.SessionNotFound))
      assertEquals(infoOpt2, Right(Session.SessionInfo("user2", List(Session.PlayerInfo("user2", false, PlayerDirection.North), Session.PlayerInfo("user1", false, PlayerDirection.East)), false)))
  }

  backendTestKit.test("session deletes when all leave") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("host", _))
      leaveRes <- backend.ask[Unit](Backend.LeaveLobby("host", _))
      foundSessionId <- backend.ask[Either[Backend.SessionNotFound, Session.Id]](Backend.FindSession("host", _))
      infoOpt <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo(sessionId, _))
    yield
      assertEquals(foundSessionId, Left(Backend.SessionNotFound))
      assertEquals(infoOpt, Left(Backend.SessionNotFound))
  }

  backendTestKit.test("assiging positions") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("u1", _))
      joinRes1 <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby(sessionId, "u2", _))
      joinRes2 <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby(sessionId, "u3", _))
      leaveRes1 <- backend.ask[Unit](Backend.LeaveLobby("u2", _))
      joinRes3 <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby(sessionId, "u4", _))
      infoOpt <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo(sessionId, _))
    yield
      assert(sessionId.nonEmpty)
      assertEquals(joinRes1, Right(()))
      assertEquals(joinRes2, Right(()))
      assertEquals(leaveRes1, ())
      assertEquals(joinRes3, Right(()))
      assertEquals(infoOpt, Right(
        Session.SessionInfo(
          "u1",
          List(
            Session.PlayerInfo("u1", false, PlayerDirection.North),
            Session.PlayerInfo("u3", false, PlayerDirection.South),
            Session.PlayerInfo("u4", false, PlayerDirection.East),
          ),
          false
        )
      ))
  }

  backendTestKit.test("force swap") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("u1", _))
      joinRes1 <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby(sessionId, "u2", _))
      joinRes2 <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby(sessionId, "u3", _))
      infoOpt1 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo(sessionId, _))
      swapRes <- backend.ask[Either[Backend.SessionNotFound, Unit]](Backend.ForceSwap(sessionId, PlayerDirection.East, PlayerDirection.South, _))
      infoOpt2 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo(sessionId, _))
    yield
      assert(sessionId.nonEmpty)
      assertEquals(joinRes1, Right(()))
      assertEquals(joinRes2, Right(()))
      assertEquals(infoOpt1, Right(
        Session.SessionInfo(
          "u1",
          List(
            Session.PlayerInfo("u1", false, PlayerDirection.North),
            Session.PlayerInfo("u2", false, PlayerDirection.East),
            Session.PlayerInfo("u3", false, PlayerDirection.South),
          ),
          false
        )
      ))
      assertEquals(swapRes, Right(()))
      assertEquals(infoOpt2, Right(
        Session.SessionInfo(
          "u1",
          List(
            Session.PlayerInfo("u1", false, PlayerDirection.North),
            Session.PlayerInfo("u2", false, PlayerDirection.South),
            Session.PlayerInfo("u3", false, PlayerDirection.East),
          ),
          false
        )
      ))
  }

  backendTestKit.test("game starts") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("u1", _))
      joinRes1 <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby(sessionId, "u2", _))
      joinRes2 <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby(sessionId, "u3", _))
      leaveRes1 <- backend.ask[Unit](Backend.LeaveLobby("u2", _))
      joinRes3 <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby(sessionId, "u4", _))
      joinRes4 <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby(sessionId, "u5", _))
      infoOpt1 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo(sessionId, _))
      readyRes1 <- backend.ask[Either[Backend.UserNotInSession, Unit]](Backend.SetUserReady("u1", true, _))
      readyRes2 <- backend.ask[Either[Backend.UserNotInSession, Unit]](Backend.SetUserReady("u3", true, _))
      readyRes3 <- backend.ask[Either[Backend.UserNotInSession, Unit]](Backend.SetUserReady("u4", true, _))
      infoOpt2 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo(sessionId, _))
      readyRes4 <- backend.ask[Either[Backend.UserNotInSession, Unit]](Backend.SetUserReady("u5", true, _))
      infoOpt3 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo(sessionId, _))
    yield
      assert(sessionId.nonEmpty)
      assertEquals(joinRes1, Right(()))
      assertEquals(joinRes2, Right(()))
      assertEquals(leaveRes1, ())
      assertEquals(joinRes3, Right(()))
      assertEquals(joinRes4, Right(()))
      assertEquals(infoOpt1, Right(
        Session.SessionInfo(
          "u1",
          List(
            Session.PlayerInfo("u1", false, PlayerDirection.North),
            Session.PlayerInfo("u3", false, PlayerDirection.South),
            Session.PlayerInfo("u4", false, PlayerDirection.East),
            Session.PlayerInfo("u5", false, PlayerDirection.West)
          ),
          false
        )
      ))
      assertEquals(infoOpt2, Right(
        Session.SessionInfo(
          "u1",
          List(
            Session.PlayerInfo("u1", true, PlayerDirection.North),
            Session.PlayerInfo("u3", true, PlayerDirection.South),
            Session.PlayerInfo("u4", true, PlayerDirection.East),
            Session.PlayerInfo("u5", false, PlayerDirection.West)
          ),
          false
        )
      ))
      assertEquals(infoOpt3, Right(
        Session.SessionInfo(
          "u1",
          List(
            Session.PlayerInfo("u1", true, PlayerDirection.North),
            Session.PlayerInfo("u3", true, PlayerDirection.South),
            Session.PlayerInfo("u4", true, PlayerDirection.East),
            Session.PlayerInfo("u5", true, PlayerDirection.West)
          ),
          true
        )
      ))
  }
}
