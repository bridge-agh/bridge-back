from fastapi import APIRouter

from bridge_back.backend.types import UserId
from bridge_back.backend.session import get_session

from . import lobby


router = APIRouter(prefix="/session")

router.include_router(lobby.router)


# --------------------------------- #


@router.post("/heartbeat")
async def heartbeat(user_id: UserId, session_id: str):
    get_session(session_id).heartbeat(user_id)


# --------------------------------- #
