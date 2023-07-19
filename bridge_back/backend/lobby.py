from uuid import uuid4
from fastapi import HTTPException
import asyncio
from bridge_back.backend.types import LobbyId, UserId
from datetime import datetime


class LobbyNotFound(HTTPException):
    def __init__(self):
        super().__init__(404, "Lobby not found")


class LobbyFull(HTTPException):
    def __init__(self):
        super().__init__(418, "Lobby is full")


class UserAlreadyJoined(HTTPException):
    def __init__(self):
        super().__init__(418, "User already joined")


class UserNotFound(HTTPException):
    def __init__(self):
        super().__init__(404, "User not found")


class Lobby:
    def __init__(self, id: LobbyId, host: UserId):
        self.id = id
        self.host = host
        self.users: list[UserId] = [host]
        self.pollers: list[asyncio.Event] = []
        self.created = datetime.now()

    async def poll(self):
        event = asyncio.Event()
        self.pollers.append(event)
        try:
            await event.wait()
        finally:
            self.pollers.remove(event)

    def notify_pollers(self):
        for poller in self.pollers:
            poller.set()

    def join(self, user: UserId):
        if len(self.users) >= 4:
            raise LobbyFull()
        if user in self.users:
            raise UserAlreadyJoined()
        self.users.append(user)
        self.notify_pollers()

    def leave(self, user: UserId):
        if user not in self.users:
            raise UserNotFound()
        self.users.remove(user)
        self.notify_pollers()


LOBBIES: dict[LobbyId, Lobby] = {}


def create_lobby(host_id: UserId) -> LobbyId:
    lobby_id = str(uuid4())
    LOBBIES[lobby_id] = Lobby(lobby_id, host_id)
    return lobby_id


def get_lobby(lobby_id: LobbyId) -> Lobby:
    if lobby_id not in LOBBIES:
        raise LobbyNotFound()
    return LOBBIES[lobby_id]


def find_lobby(user_id: UserId) -> LobbyId:
    for lobby_id, lobby in sorted(LOBBIES.items(), key=lambda x: x[1].created, reverse=True):
        if user_id in lobby.users:
            return lobby_id
    raise LobbyNotFound()
