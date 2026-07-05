from sqlalchemy import BigInteger, Boolean, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


class Base(DeclarativeBase):
    pass


class User(Base):
    __tablename__ = "user"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    email: Mapped[str] = mapped_column(String, unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(String)
    refresh_token: Mapped[str | None] = mapped_column(String, nullable=True)  # rotating


class SyncRecord(Base):
    """Opaque per-user key-value store. Payload is client-side encrypted JSON —
    the server never understands finance data (zero-knowledge)."""

    __tablename__ = "sync_record"
    __table_args__ = (UniqueConstraint("user_id", "table_name", "uuid"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(Integer, index=True)
    table_name: Mapped[str] = mapped_column(String)
    uuid: Mapped[str] = mapped_column(String)
    payload: Mapped[str] = mapped_column(Text)
    updated_at: Mapped[int] = mapped_column(BigInteger, index=True)  # epoch ms
    deleted: Mapped[bool] = mapped_column(Boolean, default=False)
