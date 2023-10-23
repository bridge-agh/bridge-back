from typing import Optional

from fastapi import APIRouter
from pydantic import BaseModel

from bridge_back import backend
from bridge_back.backend.session import PlayerPosition
from bridge_back.backend.types import SessionId, UserId

router = APIRouter(prefix="/lobby")


# --------------------------------- #


class CreateLobbyRequest(BaseModel):
    host_id: UserId


class CreateLobbyResponse(BaseModel):
    session_id: SessionId


@router.post("/create")
async def create_lobby(request: CreateLobbyRequest) -> CreateLobbyResponse:
    while True:
        try:
            old_session_id = backend.session.find_session(request.host_id)
            backend.session.get_session(old_session_id).leave(request.host_id)
        except backend.session.SessionNotFound:
            break
    session_id = backend.session.create_session(request.host_id)
    return CreateLobbyResponse(session_id=session_id)


# --------------------------------- #


class JoinLobbyRequest(BaseModel):
    user_id: UserId
    session_id: SessionId


@router.post("/join")
async def join_lobby(request: JoinLobbyRequest):
    while True:
        try:
            old_session_id = backend.session.find_session(request.user_id)
            backend.session.get_session(old_session_id).leave(request.user_id)
        except backend.session.SessionNotFound:
            break
    backend.session.get_session(request.session_id).join(request.user_id)


# --------------------------------- #


class LeaveLobbyRequest(BaseModel):
    user_id: UserId
    session_id: SessionId


@router.post("/leave")
async def leave_lobby(request: LeaveLobbyRequest):
    backend.session.get_session(request.session_id).leave(request.user_id)


# --------------------------------- #


class Player(BaseModel):
    id: UserId
    ready: bool
    position: Optional[PlayerPosition]


class GetInfoResponse(BaseModel):
    host_id: UserId
    users: list[Player]
    started: bool


@router.get("/info")
async def get_lobby_info(session_id: SessionId) -> GetInfoResponse:
    session = backend.session.get_session(session_id)
    users = [Player(
        id=user.id,
        ready=user.ready,
        position=user.position
    ) for user in session.users.values()]

    return GetInfoResponse(
        host_id=session.host_id,
        users=users,
        started=session.started,
    )


# --------------------------------- #


class ForceSwapRequest(BaseModel):
    first_position: PlayerPosition
    second_position: PlayerPosition
    session_id: SessionId


@router.post("/force-swap")
async def force_swap(request: ForceSwapRequest):
    backend.session.get_session(request.session_id).force_swap(request.first_position, request.second_position)


# --------------------------------- #


class ReadyRequest(BaseModel):
    user_id: UserId
    session_id: SessionId


@router.post("/ready")
async def set_player_ready(request: ReadyRequest):
    backend.session.get_session(request.session_id).ready(request.user_id)


# --------------------------------- #
