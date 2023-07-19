from uuid import uuid4
from fastapi import HTTPException
from bridge_back.backend.types import SessionId, UserId
from datetime import datetime


class SessionNotFound(HTTPException):
    def __init__(self):
        super().__init__(404, "Session not found")


class SessionFull(HTTPException):
    def __init__(self):
        super().__init__(418, "Session is full")


class UserAlreadyJoined(HTTPException):
    def __init__(self):
        super().__init__(418, "User already joined")


class UserNotFound(HTTPException):
    def __init__(self):
        super().__init__(404, "User not found")


class Session:
    def __init__(self, id: SessionId, host: UserId):
        self.id = id
        self.host = host
        self.users: list[UserId] = [host]
        self.created = datetime.now()

    def join(self, user: UserId):
        if len(self.users) >= 4:
            raise SessionFull()
        if user in self.users:
            raise UserAlreadyJoined()
        self.users.append(user)

    def leave(self, user: UserId):
        if user not in self.users:
            raise UserNotFound()
        self.users.remove(user)
        if len(self.users) == 0:
            del LOBBIES[self.id]
        if user == self.host:
            self.host = self.users[0]


LOBBIES: dict[SessionId, Session] = {}


def create_session(host_id: UserId) -> SessionId:
    session_id = str(uuid4())
    LOBBIES[session_id] = Session(session_id, host_id)
    return session_id


def get_session(session_id: SessionId) -> Session:
    if session_id not in LOBBIES:
        raise SessionNotFound()
    return LOBBIES[session_id]


def find_session(user_id: UserId) -> SessionId:
    for session_id, session in sorted(LOBBIES.items(), key=lambda x: x[1].created, reverse=True):
        if user_id in session.users:
            return session_id
    raise SessionNotFound()
