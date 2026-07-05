import os

os.environ["DB_PATH"] = ":memory:"

import pytest
from httpx import ASGITransport, AsyncClient

from app.db import init_db
from app.main import app


@pytest.fixture
async def client():
    await init_db()
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        yield c


async def _register(c: AsyncClient, email="a@b.com") -> dict:
    r = await c.post("/auth/register", json={"email": email, "password": "secret123"})
    assert r.status_code == 200, r.text
    return r.json()


async def test_auth_flow(client):
    tokens = await _register(client)
    assert "access" in tokens and "refresh" in tokens

    # duplicate email
    r = await client.post("/auth/register", json={"email": "a@b.com", "password": "x"})
    assert r.status_code == 409

    # login ok (rotates refresh — register's refresh is now revoked)
    r = await client.post("/auth/login", json={"email": "a@b.com", "password": "secret123"})
    assert r.status_code == 200
    tokens = r.json()

    # login wrong password
    r = await client.post("/auth/login", json={"email": "a@b.com", "password": "nope"})
    assert r.status_code == 401

    # refresh rotates
    r = await client.post("/auth/refresh", json={"refresh": r2token(tokens)})
    assert r.status_code == 200
    new = r.json()
    # old refresh now revoked
    r = await client.post("/auth/refresh", json={"refresh": r2token(tokens)})
    assert r.status_code == 401
    r = await client.post("/auth/refresh", json={"refresh": new["refresh"]})
    assert r.status_code == 200


def r2token(pair: dict) -> str:
    return pair["refresh"]


async def test_sync_roundtrip(client):
    tokens = await _register(client, "sync@b.com")
    h = {"Authorization": f"Bearer {tokens['access']}"}

    # device A pushes
    rec = {"table": "txn", "uuid": "u1", "payload": "ciphertext-1", "updatedAt": 1000}
    r = await client.post("/sync/push", json={"records": [rec]}, headers=h)
    assert r.status_code == 200 and r.json()["applied"] == 1

    # device B pulls
    r = await client.get("/sync/pull?since=0", headers=h)
    assert r.status_code == 200
    records = r.json()["records"]
    assert len(records) == 1 and records[0]["payload"] == "ciphertext-1"

    # LWW: older write ignored, newer applied
    stale = {**rec, "payload": "old", "updatedAt": 500}
    r = await client.post("/sync/push", json={"records": [stale]}, headers=h)
    assert r.json()["applied"] == 0
    newer = {**rec, "payload": "ciphertext-2", "updatedAt": 2000}
    r = await client.post("/sync/push", json={"records": [newer]}, headers=h)
    assert r.json()["applied"] == 1

    r = await client.get("/sync/pull?since=1000", headers=h)
    assert r.json()["records"][0]["payload"] == "ciphertext-2"

    # unauthenticated rejected
    r = await client.get("/sync/pull?since=0")
    assert r.status_code in (401, 403)


async def test_user_isolation(client):
    t1 = await _register(client, "u1@b.com")
    t2 = await _register(client, "u2@b.com")
    h1 = {"Authorization": f"Bearer {t1['access']}"}
    h2 = {"Authorization": f"Bearer {t2['access']}"}

    await client.post(
        "/sync/push",
        json={"records": [{"table": "txn", "uuid": "x", "payload": "secret-u1", "updatedAt": 1}]},
        headers=h1,
    )
    r = await client.get("/sync/pull?since=0", headers=h2)
    assert r.json()["records"] == []
