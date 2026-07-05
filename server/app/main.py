from contextlib import asynccontextmanager

from fastapi import FastAPI

from .auth import router as auth_router
from .db import init_db
from .sync import router as sync_router


@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    yield


app = FastAPI(title="MoneyTrack Sync", lifespan=lifespan)
app.include_router(auth_router)
app.include_router(sync_router)


@app.get("/health")
async def health():
    return {"ok": True}
