from enum import Enum
from fastapi import APIRouter
from pydantic import BaseModel

from bridge_back.backend.types import SessionId, UserId
from bridge_back import backend


router = APIRouter(prefix="/game")


# --------------------------------- #


class GameStage(str, Enum):
    BIDDING = "bidding"
    PLAYING = "playing"
    SCORING = "scoring"


class PlayerDirection(str, Enum):
    N = "n"
    E = "e"
    S = "s"
    W = "w"


class PairDirection(str, Enum):
    NS = "ns"
    EW = "ew"


Bid = str
Card = str


class BiddingObservation(BaseModel):
    first_dealer: PlayerDirection
    bid_history: list[Bid]
    bid: Bid
    declarer: PlayerDirection
    multiplier: int


class TrickTakenObservation(BaseModel):
    ns: list[Card]
    ew: list[Card]


class GameObservation(BaseModel):
    round_player: PlayerDirection
    round_cards: list[Card]
    dummy: list[Card]
    tricks: TrickTakenObservation


class PlayerObservation(BaseModel):
    game_stage: GameStage
    current_player: PlayerDirection
    bidding: BiddingObservation
    game: GameObservation
    hand: list[Card]


@router.get("/observe")
async def get_observation(session_id: SessionId, user_id: UserId) -> PlayerObservation:
    game = backend.session.get_session(session_id).get_game()
    observation = game.player_observation(...)
    ...
    return PlayerObservation(...)


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
