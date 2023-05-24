from fastapi import FastAPI

from .routers import healthcheck


app = FastAPI()
app.include_router(healthcheck.router)


@app.get("/")
async def root():
    return {"message": "Hello World Yes"}
