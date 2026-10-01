"""健康检查：无鉴权。DB 可连返回 UP，不可连也返回 200 + DOWN（避免容器启动竞态）。"""
from fastapi import APIRouter

from app import db
from app.errors import ok

router = APIRouter()


@router.get("/health")
def health() -> dict:
    return ok({"status": "UP" if db.healthy() else "DOWN"})
