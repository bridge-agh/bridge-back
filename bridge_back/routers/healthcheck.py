from fastapi import APIRouter


router = APIRouter(prefix="/healthcheck")


@router.get("/")
async def root():
    return {"status": "ok"}
