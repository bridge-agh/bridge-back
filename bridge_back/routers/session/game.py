from fastapi import APIRouter
from pydantic import BaseModel

from bridge_back.backend.types import SessionId, UserId
from bridge_back import backend


router = APIRouter(prefix="/game")


# --------------------------------- #


class GetInfoResponse(BaseModel):
    pass


@router.get("/info")
async def get_info(session_id: SessionId, user_id: UserId):
    pass


# --------------------------------- #


class GamePlayRequest(BaseModel):
    pass


@router.post("/play")
async def game_play(session_id: SessionId, user_id: UserId):
    pass


# --------------------------------- #


@router.post("/pass")
async def game_pass(session_id: SessionId, user_id: UserId):
    pass


# --------------------------------- #


@router.post("/double")
async def game_double(session_id: SessionId, user_id: UserId):
    pass


# --------------------------------- #


class GameBidRequest(BaseModel):
    pass


@router.post("/bid")
async def game_bid(session_id: SessionId, user_id: UserId):
    pass
