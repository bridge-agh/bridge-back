from fastapi import FastAPI

from .routers import healthcheck
from .routers import lobby


app = FastAPI()
app.include_router(healthcheck.router)
app.include_router(lobby.router)


@app.get("/")
async def root():
    return {"message": "Hello World Yes"}
