from fastapi import APIRouter
from pydantic import BaseModel

from bridge_back.backend.types import SessionId, UserId
from bridge_back import backend


router = APIRouter(prefix="/lobby")


class CreateLobbyRequest(BaseModel):
    host_id: UserId


class CreateLobbyResponse(BaseModel):
    session_id: SessionId


@router.post("/createLobby")
async def create_lobby(request: CreateLobbyRequest) -> CreateLobbyResponse:
    session_id = backend.session.create_session(request.host_id)
    return CreateLobbyResponse(session_id=session_id)


class JoinLobbyRequest(BaseModel):
    user_id: UserId
    session_id: SessionId


@router.post("/joinLobby")
async def join_lobby(request: JoinLobbyRequest):
    backend.session.get_session(request.session_id).join(request.user_id)


class GetLobbyResponse(BaseModel):
    host_id: UserId
    users: list[UserId]


@router.get("/getLobby")
async def get_lobby(session_id: SessionId) -> GetLobbyResponse:
    session = backend.session.get_session(session_id)
    return GetLobbyResponse(host_id=session.host, users=session.users)


class FindLobbyResponse(BaseModel):
    session_id: SessionId


@router.get("/findLobby")
async def find_lobby(user_id: UserId) -> FindLobbyResponse:
    session_id = backend.session.find_session(user_id)
    return FindLobbyResponse(session_id=session_id)
