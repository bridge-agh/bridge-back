from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .routers import healthcheck
from .routers import lobby


app = FastAPI()

origins = ["*"]

app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(healthcheck.router)
app.include_router(lobby.router)


@app.get("/")
async def root():
    return {"message": "Hello World Yes"}
