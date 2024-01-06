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
  given akka.util.Timeout = akka.util.Timeout(3000, java.util.concurrent.TimeUnit.MILLISECONDS)

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
      infoOpt <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo("host", sessionId, _))
    yield
      assertEquals(infoOpt, Right(Session.SessionInfo("host", List(Session.PlayerInfo("host", false, PlayerDirection.North, true)), false, None, 1)))
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
      joinResult <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby("guest", sessionId, _))
      infoOpt <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo("host", sessionId, _))
    yield
      assertEquals(joinResult, Right(()))
      assertEquals(infoOpt, Right(Session.SessionInfo("host", List(Session.PlayerInfo("host", false, PlayerDirection.North, true), Session.PlayerInfo("guest", false, PlayerDirection.East, true)), false, None, 1)))
  }

  backendTestKit.test("leave lobby") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("host", _))
      joinRes <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby("guest", sessionId, _))
      leaveRes <- backend.ask[Unit](Backend.LeaveSession("guest", _))
      infoOpt <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo("host", sessionId, _))
    yield
      assert(sessionId.nonEmpty)
      assertEquals(joinRes, Right(()))
      assertEquals(leaveRes, ())
      assertEquals(infoOpt, Right(Session.SessionInfo("host", List(Session.PlayerInfo("host", false, PlayerDirection.North, true)), false, None, 1)))
  }

  backendTestKit.test("set ready") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("host", _))
      joinRes <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby("guest", sessionId, _))
      readyRes1 <- backend.ask[Either[Backend.UserNotInSession, Unit]](Backend.SetUserReady("guest", true, _))
      infoOpt1 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo("host", sessionId, _))
      readyRes2 <- backend.ask[Either[Backend.UserNotInSession, Unit]](Backend.SetUserReady("host", true, _))
      infoOpt2 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo("host", sessionId, _))
      readyRes3 <- backend.ask[Either[Backend.UserNotInSession, Unit]](Backend.SetUserReady("guest", false, _))
      infoOpt3 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo("host", sessionId, _))
    yield
      assertEquals(joinRes, Right(()))
      assertEquals(readyRes1, Right(()))
      assertEquals(infoOpt1, Right(Session.SessionInfo("host", List(Session.PlayerInfo("host", false, PlayerDirection.North, true), Session.PlayerInfo("guest", true, PlayerDirection.East, true)), false, None, 1)))
      assertEquals(readyRes2, Right(()))
      assertEquals(infoOpt2, Right(Session.SessionInfo("host", List(Session.PlayerInfo("host", true, PlayerDirection.North, true), Session.PlayerInfo("guest", true, PlayerDirection.East, true)), false, None, 1)))
      assertEquals(readyRes3, Right(()))
      assertEquals(infoOpt3, Right(Session.SessionInfo("host", List(Session.PlayerInfo("host", true, PlayerDirection.North, true), Session.PlayerInfo("guest", false, PlayerDirection.East, true)), false, None, 1)))
  }

  backendTestKit.test("kick") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("host", _))
      joinRes <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby("guest", sessionId, _))
      kickRes <- backend.ask[Either[Backend.SessionNotFound | Backend.UserNotInSession, Unit]](Backend.KickUser("host", "guest", _))
      infoOpt <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo("host", sessionId, _))
    yield
      assertEquals(joinRes, Right(()))
      assertEquals(kickRes, Right(()))
      assertEquals(infoOpt, Right(Session.SessionInfo("host", List(Session.PlayerInfo("host", false, PlayerDirection.North, true)), false, None, 1)))
  }

  backendTestKit.test("create lobby when in lobby") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId1 <- backend.ask[Session.Id](Backend.CreateLobby("host", _))
      sessionId2 <- backend.ask[Session.Id](Backend.CreateLobby("host", _))
      foundSessionId <- backend.ask[Either[Backend.SessionNotFound, Session.Id]](Backend.FindSession("host", _))
      infoOpt1 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo("host", sessionId1, _))
      infoOpt2 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo("host", sessionId2, _))
    yield
      assertEquals(foundSessionId, Right(sessionId2))
      assertEquals(infoOpt1, Left(Backend.SessionNotFound))
      assertEquals(infoOpt2, Right(Session.SessionInfo("host", List(Session.PlayerInfo("host", false, PlayerDirection.North, true)), false, None, 1)))
  }

  backendTestKit.test("join lobby when in lobby") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId1 <- backend.ask[Session.Id](Backend.CreateLobby("user1", _))
      sessionId2 <- backend.ask[Session.Id](Backend.CreateLobby("user2", _))
      joinRes <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby("user1", sessionId2, _))
      foundSessionId <- backend.ask[Either[Backend.SessionNotFound, Session.Id]](Backend.FindSession("user1", _))
      infoOpt1 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo("host", sessionId1, _))
      infoOpt2 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo("host", sessionId2, _))
    yield
      assertEquals(foundSessionId, Right(sessionId2))
      assertEquals(infoOpt1, Left(Backend.SessionNotFound))
      assertEquals(infoOpt2, Right(Session.SessionInfo("user2", List(Session.PlayerInfo("user2", false, PlayerDirection.North, true), Session.PlayerInfo("user1", false, PlayerDirection.East, true)), false, None, 1)))
  }

  backendTestKit.test("session deletes when all leave") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("host", _))
      leaveRes <- backend.ask[Unit](Backend.LeaveSession("host", _))
      foundSessionId <- backend.ask[Either[Backend.SessionNotFound, Session.Id]](Backend.FindSession("host", _))
      infoOpt <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo("host", sessionId, _))
    yield
      assertEquals(foundSessionId, Left(Backend.SessionNotFound))
      assertEquals(infoOpt, Left(Backend.SessionNotFound))
  }

  backendTestKit.test("assiging positions") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("u1", _))
      joinRes1 <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby("u2", sessionId, _))
      joinRes2 <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby("u3", sessionId, _))
      leaveRes1 <- backend.ask[Unit](Backend.LeaveSession("u2", _))
      joinRes3 <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby("u4", sessionId, _))
      infoOpt <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo("host", sessionId, _))
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
            Session.PlayerInfo("u1", false, PlayerDirection.North, true),
            Session.PlayerInfo("u3", false, PlayerDirection.South, true),
            Session.PlayerInfo("u4", false, PlayerDirection.East, true),
          ),
          false,
          None,
          1,
        )
      ))
  }

  backendTestKit.test("force swap") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("u1", _))
      joinRes1 <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby("u2", sessionId, _))
      joinRes2 <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby("u3", sessionId, _))
      infoOpt1 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo("host", sessionId, _))
      swapRes <- backend.ask[Either[Backend.SessionNotFound, Unit]](Backend.ForceSwap(sessionId, PlayerDirection.East, PlayerDirection.South, _))
      infoOpt2 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo("host", sessionId, _))
    yield
      assert(sessionId.nonEmpty)
      assertEquals(joinRes1, Right(()))
      assertEquals(joinRes2, Right(()))
      assertEquals(infoOpt1, Right(
        Session.SessionInfo(
          "u1",
          List(
            Session.PlayerInfo("u1", false, PlayerDirection.North, true),
            Session.PlayerInfo("u2", false, PlayerDirection.East, true),
            Session.PlayerInfo("u3", false, PlayerDirection.South, true),
          ),
          false,
          None,
          1,
        )
      ))
      assertEquals(swapRes, Right(()))
      assertEquals(infoOpt2, Right(
        Session.SessionInfo(
          "u1",
          List(
            Session.PlayerInfo("u1", false, PlayerDirection.North, true),
            Session.PlayerInfo("u2", false, PlayerDirection.South, true),
            Session.PlayerInfo("u3", false, PlayerDirection.East, true),
          ),
          false,
          None,
          1,
        )
      ))
  }

  backendTestKit.test("set assistant level") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("host", _))
      infoOpt1 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo("host", sessionId, _))
      setLevelRes <- backend.ask[Either[Backend.SessionNotFound, Unit]](Backend.SetAssistantLevel(sessionId, 3, _))
      infoOpt2 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo("host", sessionId, _))
    yield
      assert(sessionId.nonEmpty)
      assertEquals(infoOpt1, Right(
        Session.SessionInfo(
          "host",
          List(Session.PlayerInfo("host", false, PlayerDirection.North, true)),
          false,
          None,
          1,
        )
      ))
      assertEquals(setLevelRes, Right(()))
      assertEquals(infoOpt2, Right(
        Session.SessionInfo(
          "host", 
          List(Session.PlayerInfo("host", false, PlayerDirection.North, true)),
          false,
          None,
          3,
        )
      ))
  }

  backendTestKit.test("game starts") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("u1", _))
      joinRes1 <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby("u2", sessionId, _))
      joinRes2 <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby("u3", sessionId, _))
      leaveRes1 <- backend.ask[Unit](Backend.LeaveSession("u2", _))
      joinRes3 <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby("u4", sessionId, _))
      joinRes4 <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby("u5", sessionId, _))
      infoOpt1 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo("u1", sessionId, _))
      readyRes1 <- backend.ask[Either[Backend.UserNotInSession, Unit]](Backend.SetUserReady("u1", true, _))
      readyRes2 <- backend.ask[Either[Backend.UserNotInSession, Unit]](Backend.SetUserReady("u3", true, _))
      readyRes3 <- backend.ask[Either[Backend.UserNotInSession, Unit]](Backend.SetUserReady("u4", true, _))
      infoOpt2 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo("u1", sessionId, _))
      readyRes4 <- backend.ask[Either[Backend.UserNotInSession, Unit]](Backend.SetUserReady("u5", true, _))
      infoOpt3 <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo("u1", sessionId, _))
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
            Session.PlayerInfo("u1", false, PlayerDirection.North, true),
            Session.PlayerInfo("u3", false, PlayerDirection.South, true),
            Session.PlayerInfo("u4", false, PlayerDirection.East, true),
            Session.PlayerInfo("u5", false, PlayerDirection.West, true)
          ),
          false,
          None,
          1,
        )
      ))
      assertEquals(infoOpt2, Right(
        Session.SessionInfo(
          "u1",
          List(
            Session.PlayerInfo("u1", true, PlayerDirection.North, true),
            Session.PlayerInfo("u3", true, PlayerDirection.South, true),
            Session.PlayerInfo("u4", true, PlayerDirection.East, true),
            Session.PlayerInfo("u5", false, PlayerDirection.West, true)
          ),
          false,
          None,
          1,
        )
      ))
      assertEquals(infoOpt3, Right(
        Session.SessionInfo(
          "u1",
          List(
            Session.PlayerInfo("u1", true, PlayerDirection.North, true),
            Session.PlayerInfo("u3", true, PlayerDirection.South, true),
            Session.PlayerInfo("u4", true, PlayerDirection.East, true),
            Session.PlayerInfo("u5", true, PlayerDirection.West, true)
          ),
          true,
          infoOpt3.map(_.playerObservation).toOption.flatten,
          1,
        )
      ))
      assert(infoOpt3.map(_.playerObservation).toOption.flatten.nonEmpty)
  }

  backendTestKit.test("add assistant to lobby") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("u1", _))
      setRes <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.AddAssistant(sessionId, PlayerDirection.East, _))
      infoOpt <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo("u1", sessionId, _))
    yield
      assert(sessionId.nonEmpty)
      assertEquals(setRes, Right(()))
      assertEquals(infoOpt, Right(
        Session.SessionInfo(
          "u1",
          List(
            Session.PlayerInfo("u1", false, PlayerDirection.North, true),
            Session.PlayerInfo(infoOpt.right.get.users(1).id, true, PlayerDirection.East, false),
          ),
          false,
          None,
          1,
        )
      ))
  }

  backendTestKit.test("promote to host") { (testKit, backend) =>
    given ActorSystem[_] = testKit.system
    for
      sessionId <- backend.ask[Session.Id](Backend.CreateLobby("u1", _))
      joinRes <- backend.ask[Either[Backend.SessionNotFound | Session.SessionFull, Unit]](Backend.JoinLobby("u2", sessionId, _))
      promoteRes <- backend.ask[Either[Backend.SessionNotFound | Backend.UserNotInSession, Unit]](Backend.PromoteUserToHost("u1", "u2", _))
      infoOpt <- backend.ask[Either[Backend.SessionNotFound, Session.SessionInfo]](Backend.GetLobbyInfo("u1", sessionId, _))
    yield
      assert(sessionId.nonEmpty)
      assertEquals(joinRes, Right(()))
      assertEquals(promoteRes, Right(()))
      assertEquals(infoOpt, Right(
        Session.SessionInfo(
          "u2",
          List(
            Session.PlayerInfo("u1", false, PlayerDirection.North, true),
            Session.PlayerInfo("u2", false, PlayerDirection.East, true),
          ),
          false,
          None,
          1,
        )
      ))
  }
}
