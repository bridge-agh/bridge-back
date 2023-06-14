from uuid import uuid4
from fastapi import HTTPException
from bridge_back.backend.types import LobbyId, UserId


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

    def join(self, user: UserId):
        if len(self.users) >= 4:
            raise LobbyFull()
        if user in self.users:
            raise UserAlreadyJoined()
        self.users.append(user)

    def leave(self, user: UserId):
        if user not in self.users:
            raise UserNotFound()
        self.users.remove(user)


LOBBIES: dict[LobbyId, Lobby] = {}


def create_lobby(host_id: UserId) -> LobbyId:
    lobby_id = str(uuid4())
    LOBBIES[lobby_id] = Lobby(lobby_id, host_id)
    return lobby_id


def get_lobby(lobby_id: LobbyId) -> Lobby:
    if lobby_id not in LOBBIES:
        raise LobbyNotFound()
    return LOBBIES[lobby_id]
