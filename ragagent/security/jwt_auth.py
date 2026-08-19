from __future__ import annotations

from dataclasses import dataclass

import jwt
from fastapi import HTTPException, status

from ragagent.settings import Settings


@dataclass(frozen=True)
class AuthenticatedUser:
    user_id: int
    token: str


class JwtAuthenticator:
    def __init__(self, settings: Settings):
        self._secret = settings.jwt_secret

    def authenticate(self, authorization: str | None) -> AuthenticatedUser:
        if not authorization or not authorization.startswith("Bearer "):
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="缺少 Bearer Token")
        if len(self._secret.encode("utf-8")) < 32:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Agent JWT 验证尚未配置",
            )
        token = authorization[7:].strip()
        try:
            claims = jwt.decode(token, self._secret, algorithms=["HS256"], options={"require": ["sub", "exp"]})
            user_id = int(claims["sub"])
        except (jwt.PyJWTError, TypeError, ValueError) as exc:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token 无效或已过期") from exc
        return AuthenticatedUser(user_id=user_id, token=token)
