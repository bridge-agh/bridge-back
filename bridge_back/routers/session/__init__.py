from fastapi import APIRouter
from pydantic import BaseModel

from bridge_back.backend.types import UserId
from bridge_back.backend.session import get_session, find_session
from . import lobby


router = APIRouter(prefix="/session")

router.include_router(lobby.router)


# --------------------------------- #


@router.post("/heartbeat")
async def heartbeat(user_id: UserId, session_id: str):
    get_session(session_id).heartbeat(user_id)


# --------------------------------- #


class FindSessionResponse(BaseModel):
    session_id: str


@router.get("/find")
async def find_session(user_id: UserId):
    session_id = find_session(user_id)
    return FindSessionResponse(session_id=session_id)
