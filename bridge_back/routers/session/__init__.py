from fastapi import APIRouter

from . import lobby


router = APIRouter(prefix="/session")

router.include_router(lobby.router)
