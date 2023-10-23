from enum import Enum
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


class PlayerDirection(Enum):
    NORTH = 0
    EAST = 1
    SOUTH = 2
    WEST = 3


class User:
    def __init__(self, id: UserId):
        self.id = id
        self.ready = False
        self.last_heartbeat = datetime.now()
        self.position = None


class Session:
    def __init__(self, session_id: SessionId, host_id: UserId):
        self.session_id = session_id
        self.host_id = host_id
        self.users: dict[UserId, User] = {host_id: User(host_id)}
        self.users[host_id].position = PlayerDirection.NORTH
        self.created = datetime.now()
        self.started = False

    def join(self, user_id: UserId):
        if len(self.users) >= 4:
            raise SessionFull()
        if user_id in self.users:
            raise UserAlreadyJoined()
        self.users[user_id] = User(user_id)
        for position in PlayerDirection:
            if position not in [user.position for user in self.users.values()]:
                self.users[user_id].position = position
                break

    def force_swap(self, first_position: PlayerDirection, second_position: PlayerDirection):
        for user in self.users.values():
            if user.position == first_position:
                user.position = second_position
            elif user.position == second_position:
                user.position = first_position

    def leave(self, user_id: UserId):
        if user_id not in self.users:
            raise UserNotFound()
        del self.users[user_id]
        if len(self.users) == 0:
            del SESSIONS[self.session_id]
            return
        if user_id == self.host_id:
            self.host_id = next(iter(self.users))

    def ready(self, user_id: UserId):
        if user_id not in self.users:
            raise UserNotFound()
        self.users[user_id].ready = True
        if all(user.ready for user in self.users.values()):
            self.started = True

    def heartbeat(self, user_id: UserId):
        if user_id not in self.users:
            raise UserNotFound()
        self.users[user_id].last_heartbeat = datetime.now()


SESSIONS: dict[SessionId, Session] = {}


def create_session(host_id: UserId) -> SessionId:
    session_id = str(uuid4())
    SESSIONS[session_id] = Session(session_id, host_id)
    return session_id


def get_session(session_id: SessionId) -> Session:
    if session_id not in SESSIONS:
        raise SessionNotFound()
    return SESSIONS[session_id]


def find_session(user_id: UserId) -> SessionId:
    for session_id, session in sorted(SESSIONS.items(), key=lambda x: x[1].created, reverse=True):
        if user_id in session.users:
            return session_id
    raise SessionNotFound()
