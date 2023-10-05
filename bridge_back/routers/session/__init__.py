from fastapi import APIRouter
from pydantic import BaseModel

from bridge_back.backend.types import UserId
from bridge_back import backend
from . import lobby, game


router = APIRouter(prefix="/session")

router.include_router(lobby.router)
router.include_router(game.router)


# --------------------------------- #


class HeartbeatRequest(BaseModel):
    user_id: UserId
    session_id: str


@router.post("/heartbeat")
async def heartbeat(request: HeartbeatRequest):
    backend.session.get_session(request.session_id).heartbeat(request.user_id)


# --------------------------------- #


class FindSessionResponse(BaseModel):
    session_id: str


@router.get("/find")
async def find_session(user_id: UserId):
    session_id = backend.session.find_session(user_id)
    return FindSessionResponse(session_id=session_id)
