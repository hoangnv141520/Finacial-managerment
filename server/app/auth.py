import time
from collections import defaultdict
from datetime import datetime, timedelta, timezone

import jwt
from argon2 import PasswordHasher
from argon2.exceptions import VerifyMismatchError
from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, EmailStr
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from .config import settings
from .db import get_session
from .models import User

router = APIRouter(prefix="/auth", tags=["auth"])
ph = PasswordHasher()
bearer = HTTPBearer()

# ponytail: in-memory rate limit, enough for 1 instance; Redis when scaling out
_attempts: dict[str, list[float]] = defaultdict(list)
RATE_LIMIT = 5
RATE_WINDOW = 60.0


def _check_rate(ip: str) -> None:
    now = time.monotonic()
    _attempts[ip] = [t for t in _attempts[ip] if now - t < RATE_WINDOW]
    if len(_attempts[ip]) >= RATE_LIMIT:
        raise HTTPException(429, "Too many attempts, try later")
    _attempts[ip].append(now)


class Credentials(BaseModel):
    email: EmailStr
    password: str


class TokenPair(BaseModel):
    access: str
    refresh: str


class RefreshBody(BaseModel):
    refresh: str


def _make_token(user_id: int, kind: str, ttl: timedelta) -> str:
    return jwt.encode(
        {"sub": str(user_id), "kind": kind, "exp": datetime.now(timezone.utc) + ttl},
        settings.jwt_secret,
        algorithm="HS256",
    )


def _decode(token: str, kind: str) -> int:
    try:
        payload = jwt.decode(token, settings.jwt_secret, algorithms=["HS256"])
    except jwt.PyJWTError:
        raise HTTPException(401, "Invalid token")
    if payload.get("kind") != kind:
        raise HTTPException(401, "Wrong token kind")
    return int(payload["sub"])


def _pair(user_id: int) -> TokenPair:
    return TokenPair(
        access=_make_token(user_id, "access", timedelta(minutes=settings.access_ttl_minutes)),
        refresh=_make_token(user_id, "refresh", timedelta(days=settings.refresh_ttl_days)),
    )


async def current_user_id(
    creds: HTTPAuthorizationCredentials = Depends(bearer),
) -> int:
    return _decode(creds.credentials, "access")


@router.post("/register", response_model=TokenPair)
async def register(body: Credentials, session: AsyncSession = Depends(get_session)):
    existing = await session.scalar(select(User).where(User.email == body.email))
    if existing:
        raise HTTPException(409, "Email already registered")
    user = User(email=body.email, password_hash=ph.hash(body.password))
    session.add(user)
    await session.commit()
    pair = _pair(user.id)
    user.refresh_token = pair.refresh
    await session.commit()
    return pair


@router.post("/login", response_model=TokenPair)
async def login(body: Credentials, request: Request, session: AsyncSession = Depends(get_session)):
    _check_rate(request.client.host if request.client else "unknown")
    user = await session.scalar(select(User).where(User.email == body.email))
    if user is None:
        raise HTTPException(401, "Bad credentials")
    try:
        ph.verify(user.password_hash, body.password)
    except VerifyMismatchError:
        raise HTTPException(401, "Bad credentials")
    pair = _pair(user.id)
    user.refresh_token = pair.refresh  # rotate
    await session.commit()
    return pair


@router.post("/refresh", response_model=TokenPair)
async def refresh(body: RefreshBody, session: AsyncSession = Depends(get_session)):
    user_id = _decode(body.refresh, "refresh")
    user = await session.get(User, user_id)
    if user is None or user.refresh_token != body.refresh:
        raise HTTPException(401, "Refresh token revoked")
    pair = _pair(user.id)
    user.refresh_token = pair.refresh
    await session.commit()
    return pair
