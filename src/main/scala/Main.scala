import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.actor.typed.scaladsl.AskPattern._

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

given akka.util.Timeout = akka.util.Timeout(1000, java.util.concurrent.TimeUnit.MILLISECONDS)

object User {
  type Actor = ActorRef[Command]
  type Id = String

  sealed trait Command
  final case class Join(session: Session.Actor, replyTo: ActorRef[JoinResult]) extends Command
  case object Leave extends Command
  final case class SetReady(ready: Boolean) extends Command
  final case class GetReady(replyTo: ActorRef[Boolean]) extends Command
  final case class GetId(replyTo: ActorRef[Id]) extends Command

  sealed trait JoinResult extends Command
  case object Joined extends JoinResult
  case object SessionFull extends JoinResult

  def apply(id: Id) = unjoined(id)

  private def unjoined(id: Id): Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case Join(session, replyTo) =>
          session ! Session.Join(context.self, context.self)
          joining(id, session, replyTo)
        case GetId(replyTo) =>
          replyTo ! id
          Behaviors.same
        case _ => Behaviors.unhandled
      }
    }

  private def joining(id: Id, session: Session.Actor, replyTo: ActorRef[JoinResult]): Behavior[Command] =
    Behaviors.receiveMessage {
      case Joined =>
        replyTo ! Joined
        joined(id, session)
      case SessionFull =>
        replyTo ! SessionFull
        unjoined(id)
      case GetId(replyTo) =>
        replyTo ! id
        Behaviors.same
      case _ => Behaviors.unhandled
    }

  private def joined(id: Id, session: Session.Actor, ready: Boolean = false): Behavior[Command] =
    Behaviors.receiveMessage {
      case Leave =>
        unjoined(id)
      case SetReady(ready) =>
        joined(id, session, ready)
      case GetReady(replyTo) =>
        replyTo ! ready
        Behaviors.same
      case GetId(replyTo) =>
        replyTo ! id
        Behaviors.same
      case _ => Behaviors.unhandled
    }
}

object Session {
  type Actor = ActorRef[Command]
  type Id = String

  sealed trait Command
  final case class Join(user: User.Actor, replyTo: ActorRef[User.JoinResult]) extends Command
  final case class GetLobbyInfo(replyTo: ActorRef[LobbyInfo]) extends Command

  final case class Player(id: User.Id, ready: Boolean, position: Int)
  final case class LobbyInfo(host: User.Id, users: List[Player], started: Boolean)

  def apply(id: Id): Behavior[Command] = apply(id, List.empty)

  private def apply(id: Id, users: List[User.Actor]): Behavior[Command] =
    Behaviors.setup { context =>
      given ActorSystem[_] = context.system
      given ExecutionContext = context.executionContext
      Behaviors.receiveMessage {
        case Join(user, replyTo) =>
          if (users.length < 4) {
            replyTo ! User.Joined
            apply(id, users :+ user)
          } else {
            replyTo ! User.SessionFull
            Behaviors.same
          }
        case GetLobbyInfo(replyTo) =>
          val started = false
          val host = users.head
          val hostId = host.ask[User.Id](User.GetId(_))
          val ids = Future.sequence(users map { user =>
            user.ask[User.Id](User.GetId(_))
          })
          val ready =  Future.sequence(users map { user =>
            user.ask[Boolean](User.GetReady(_))
          })
          val players = for {
            ids <- ids
            ready <- ready
          } yield ids.zip(ready).zipWithIndex.map { case ((id, ready), position) =>
            Player(id, ready, position)
          }
          val info = for {
            hostId <- hostId
            players <- players
          } yield LobbyInfo(hostId, players, started)
          info map (replyTo ! _)
          Behaviors.same
      }
    }
}

object Backend {
  type Actor = ActorRef[Command]

  sealed trait Command
  
  final case class Heartbeat(userId: User.Id) extends Command
  final case class FindSession(userId: User.Id, replyTo: ActorRef[Option[Session.Id]]) extends Command

  final case class CreateLobby(hostId: User.Id, replyTo: ActorRef[Session.Id]) extends Command
  final case class JoinLobby(sessionId: Session.Id, userId: User.Id) extends Command
  final case class LeaveLobby(userId: User.Id) extends Command
  final case class GetLobbyInfo(sessionId: Session.Id, replyTo: ActorRef[Session.LobbyInfo]) extends Command
  final case class SetUserReady(userId: User.Id, ready: Boolean) extends Command

  def apply(users: Map[User.Id, User.Actor] = Map.empty,
            sessions: Map[Session.Id, Session.Actor] = Map.empty,
           ): Behavior[Command] = Behaviors.setup { context =>
    given ActorSystem[_] = context.system
    given ExecutionContext = context.executionContext
    Behaviors.receiveMessage {
      case Heartbeat(userId) =>
        Behaviors.same
      case FindSession(userId, replyTo) =>
        val user = users.get(userId)
        Behaviors.same
      case CreateLobby(hostId, replyTo) =>
        val host = context.spawn(User(hostId), "")
        val sessionId = java.util.UUID.randomUUID.toString
        val session = context.spawn(Session(sessionId), "")
        val joinFuture = host.ask[User.JoinResult](User.Join(session, _))
        replyTo ! sessionId
        apply(
          users + (hostId -> host),
          sessions + (sessionId -> session),
        )
      case JoinLobby(sessionId, userId) =>
        Behaviors.same
      case LeaveLobby(userId) =>
        Behaviors.same
      case GetLobbyInfo(sessionId, replyTo) =>
        Behaviors.same
      case SetUserReady(userId, ready) =>
        Behaviors.same
    }
  }
}

object HttpServer {

  // POST /session/heartbeat

  private final case class HeartbeatRequest(userId: User.Id)
  private given RootJsonFormat[HeartbeatRequest] = jsonFormat1(HeartbeatRequest.apply)

  // GET /session/find

  private final case class FindSessionResponse(sessionId: Option[Session.Id])
  private given RootJsonFormat[FindSessionResponse] = jsonFormat1(FindSessionResponse.apply)

  // POST /session/lobby/create

  private final case class CreateLobbyRequest(hostId: User.Id)
  private given RootJsonFormat[CreateLobbyRequest] = jsonFormat1(CreateLobbyRequest.apply)

  private final case class CreateLobbyResponse(sessionId: Session.Id)
  private given RootJsonFormat[CreateLobbyResponse] = jsonFormat1(CreateLobbyResponse.apply)

  // POST /session/lobby/join

  private final case class JoinLobbyRequest(sessionId: Session.Id, userId: User.Id)
  private given RootJsonFormat[JoinLobbyRequest] = jsonFormat2(JoinLobbyRequest.apply)

  // POST /session/lobby/leave

  private final case class LeaveLobbyRequest(userId: User.Id)
  private given RootJsonFormat[LeaveLobbyRequest] = jsonFormat1(LeaveLobbyRequest.apply)

  // GET /session/lobby/info

  private final case class PlayerModel(id: User.Id, ready: Boolean, position: Int)
  private given RootJsonFormat[PlayerModel] = jsonFormat3(PlayerModel.apply)

  private final case class GetLobbyInfoResponse(hostId: User.Id, users: List[PlayerModel], started: Boolean)
  private given RootJsonFormat[GetLobbyInfoResponse] = jsonFormat3(GetLobbyInfoResponse.apply)

  // POST /session/lobby/ready

  private final case class SetUserReadyRequest(userId: User.Id, ready: Boolean)
  private given RootJsonFormat[SetUserReadyRequest] = jsonFormat2(SetUserReadyRequest.apply)


  def apply(backend: Backend.Actor): Behavior[Nothing] = Behaviors.setup { context =>
    given ActorSystem[_] = context.system
    given ExecutionContext = context.executionContext

    val route =
      concat(
        path("healthcheck") {
          get {
            complete(HttpEntity(ContentTypes.`application/json`, """{"status": "ok"}"""))
          }
        },
        pathPrefix("session") {
          concat(
            path("heartbeat") {
              post {
                entity(as[HeartbeatRequest]) { request =>
                  backend ! Backend.Heartbeat(request.userId)
                  complete(StatusCodes.OK)
                }
              }
            },
            path("find") {
              get {
                parameter("userId".as[User.Id]) { userId =>
                  val session = backend.ask[Option[Session.Id]](Backend.FindSession(userId, _))
                  complete(session map (FindSessionResponse(_)))
                }
              }
            },
            pathPrefix("lobby") {
              concat(
                path("create") {
                  post {
                    entity(as[CreateLobbyRequest]) { request =>
                      val session = backend.ask[Session.Id](Backend.CreateLobby(request.hostId, _))
                      complete(session map (CreateLobbyResponse(_)))
                    }
                  }
                },
                path("join") {
                  post {
                    entity(as[JoinLobbyRequest]) { request =>
                      backend ! Backend.JoinLobby(request.sessionId, request.userId)
                      complete(StatusCodes.OK)
                    }
                  }
                },
                path("leave") {
                  post {
                    entity(as[LeaveLobbyRequest]) { request =>
                      backend ! Backend.LeaveLobby(request.userId)
                      complete(StatusCodes.OK)
                    }
                  }
                },
                path("info") {
                  get {
                    parameter("sessionId".as[Session.Id]) { sessionId =>
                      val fInfo = backend.ask[Session.LobbyInfo](Backend.GetLobbyInfo(sessionId, _))
                      complete(fInfo map { info => GetLobbyInfoResponse(
                        info.host,
                        info.users map { user => PlayerModel(user.id, user.ready, user.position) },
                        info.started,
                      )})
                    }
                  }
                },
                path("ready") {
                  post {
                    entity(as[SetUserReadyRequest]) { request =>
                      backend ! Backend.SetUserReady(request.userId, request.ready)
                      complete(StatusCodes.OK)
                    }
                  }
                },
              )
            },
            pathPrefix("game") {
              concat(
                path("info") {
                  get {
                    complete(StatusCodes.NotImplemented)
                  }
                },
                path("play") {
                  post {
                    complete(StatusCodes.NotImplemented)
                  }
                },
                path("pass") {
                  post {
                    complete(StatusCodes.NotImplemented)
                  }
                },
                path("double") {
                  post {
                    complete(StatusCodes.NotImplemented)
                  }
                },
                path("bid") {
                  post {
                    complete(StatusCodes.NotImplemented)
                  }
                },
              )
            },
          )
        },
      )

    val bindingFuture = Http().newServerAt("localhost", 8000).bind(route)

    Behaviors.empty
  }
}

object MainSystem {
  def apply(): Behavior[Nothing] = Behaviors.setup { context =>
    val backend = context.spawn(Backend(), "backend")
    val httpServer = context.spawn(HttpServer(backend), "http-server")
    Behaviors.empty
  }
}

@main def run: Unit =
  val system = ActorSystem(MainSystem(), "main-system")
