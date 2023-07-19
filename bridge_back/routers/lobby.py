from fastapi import APIRouter
from pydantic import BaseModel

from bridge_back.backend.types import LobbyId, UserId
from bridge_back import backend


router = APIRouter(prefix="/lobby")


class CreateLobbyRequest(BaseModel):
    host_id: UserId


class CreateLobbyResponse(BaseModel):
    lobby_id: LobbyId


@router.post("/createLobby")
async def create_lobby(request: CreateLobbyRequest) -> CreateLobbyResponse:
    lobby_id = backend.lobby.create_lobby(request.host_id)
    return CreateLobbyResponse(lobby_id=lobby_id)


class JoinLobbyRequest(BaseModel):
    user_id: UserId
    lobby_id: LobbyId


@router.post("/joinLobby")
async def join_lobby(request: JoinLobbyRequest):
    backend.lobby.get_lobby(request.lobby_id).join(request.user_id)


class GetLobbyResponse(BaseModel):
    host_id: UserId
    users: list[UserId]


@router.get("/getLobby")
async def get_lobby(lobby_id: LobbyId, poll: bool = False) -> GetLobbyResponse:
    lobby = backend.lobby.get_lobby(lobby_id)
    if poll:
        await lobby.poll()
    return GetLobbyResponse(host_id=lobby.host, users=lobby.users)


class FindLobbyResponse(BaseModel):
    lobby_id: LobbyId


@router.get("/findLobby")
async def find_lobby(user_id: UserId) -> FindLobbyResponse:
    lobby_id = backend.lobby.find_lobby(user_id)
    return FindLobbyResponse(lobby_id=lobby_id)
