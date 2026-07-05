from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    jwt_secret: str = "dev-secret-change-me-0123456789abcdef"  # override via JWT_SECRET env in prod
    db_path: str = "moneytrack.db"
    access_ttl_minutes: int = 15
    refresh_ttl_days: int = 30

    class Config:
        env_file = ".env"


settings = Settings()
