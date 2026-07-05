import time

from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from .auth import current_user_id
from .db import get_session
from .models import SyncRecord

router = APIRouter(prefix="/sync", tags=["sync"])


class RecordIn(BaseModel):
    table: str
    uuid: str
    payload: str  # client-side encrypted blob
    updatedAt: int
    deleted: bool = False


class PushBody(BaseModel):
    records: list[RecordIn]


class RecordOut(RecordIn):
    pass


class PullResponse(BaseModel):
    records: list[RecordOut]
    serverTime: int


@router.post("/push")
async def push(
    body: PushBody,
    user_id: int = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
):
    applied = 0
    for r in body.records:
        existing = await session.scalar(
            select(SyncRecord).where(
                SyncRecord.user_id == user_id,
                SyncRecord.table_name == r.table,
                SyncRecord.uuid == r.uuid,
            )
        )
        if existing is None:
            session.add(
                SyncRecord(
                    user_id=user_id,
                    table_name=r.table,
                    uuid=r.uuid,
                    payload=r.payload,
                    updated_at=r.updatedAt,
                    deleted=r.deleted,
                )
            )
            applied += 1
        elif r.updatedAt > existing.updated_at:  # last-write-wins
            existing.payload = r.payload
            existing.updated_at = r.updatedAt
            existing.deleted = r.deleted
            applied += 1
    await session.commit()
    return {"applied": applied}


@router.get("/pull", response_model=PullResponse)
async def pull(
    since: int = 0,
    user_id: int = Depends(current_user_id),
    session: AsyncSession = Depends(get_session),
):
    rows = (
        await session.scalars(
            select(SyncRecord).where(SyncRecord.user_id == user_id, SyncRecord.updated_at > since)
        )
    ).all()
    return PullResponse(
        records=[
            RecordOut(
                table=r.table_name,
                uuid=r.uuid,
                payload=r.payload,
                updatedAt=r.updated_at,
                deleted=r.deleted,
            )
            for r in rows
        ],
        serverTime=int(time.time() * 1000),
    )
