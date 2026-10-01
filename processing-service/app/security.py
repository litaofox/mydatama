"""JWT(HS256) 鉴权。与 Java 平台共享 JWT_HMAC_SECRET，算法固定 HS256，校验 exp。"""
import jwt
from fastapi import Header

from app import config
from app.errors import ApiError, ERR_TOKEN_EXPIRED, ERR_UNAUTHORIZED


def decode_token(token: str) -> dict:
    try:
        return jwt.decode(token, config.JWT_HMAC_SECRET, algorithms=[config.JWT_ALGORITHM])
    except jwt.ExpiredSignatureError:
        raise ApiError(ERR_TOKEN_EXPIRED, "Token 已过期")
    except jwt.PyJWTError:
        raise ApiError(ERR_UNAUTHORIZED, "未认证")


def require_auth(authorization: str | None = Header(default=None, alias="Authorization")) -> dict:
    """FastAPI 依赖：校验 Authorization: Bearer <JWT>，返回 claims。"""
    if not authorization or not authorization.startswith("Bearer "):
        raise ApiError(ERR_UNAUTHORIZED, "未认证")
    token = authorization[len("Bearer "):].strip()
    if not token:
        raise ApiError(ERR_UNAUTHORIZED, "未认证")
    return decode_token(token)


def actor_from_claims(claims: dict) -> str:
    """操作人：优先 claim username，其次 sub。"""
    return claims.get("username") or claims.get("sub") or "unknown"
