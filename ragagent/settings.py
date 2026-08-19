from __future__ import annotations

import os
from dataclasses import dataclass


def _bool_env(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class Settings:
    environment: str
    jwt_secret: str
    java_base_url: str
    agent_service_token: str
    redis_url: str
    checkpoint_backend: str
    planner_mode: str
    max_steps: int
    request_timeout_seconds: float
    run_ttl_seconds: int
    cors_origins: tuple[str, ...]
    allow_indexing: bool

    @classmethod
    def from_env(cls) -> "Settings":
        origins = tuple(
            item.strip()
            for item in os.getenv("AGENT_CORS_ORIGINS", "http://localhost:8501").split(",")
            if item.strip()
        )
        return cls(
            environment=os.getenv("AGENT_ENV", "dev"),
            jwt_secret=os.getenv("JWT_SECRET", ""),
            java_base_url=os.getenv("JAVA_TOOL_BASE_URL", "http://127.0.0.1:8080"),
            agent_service_token=os.getenv("AGENT_SERVICE_TOKEN", ""),
            redis_url=os.getenv("REDIS_URL", "redis://127.0.0.1:6379/1"),
            checkpoint_backend=os.getenv("AGENT_CHECKPOINT_BACKEND", "memory").lower(),
            planner_mode=os.getenv("AGENT_PLANNER_MODE", "auto").lower(),
            max_steps=int(os.getenv("AGENT_MAX_STEPS", "6")),
            request_timeout_seconds=float(os.getenv("AGENT_TOOL_TIMEOUT_SECONDS", "8")),
            run_ttl_seconds=int(os.getenv("AGENT_RUN_TTL_SECONDS", "604800")),
            cors_origins=origins,
            allow_indexing=_bool_env("RAG_ALLOW_INDEXING", False),
        )

    def validate(self) -> None:
        if self.environment.lower() in {"prod", "production"}:
            if len(self.jwt_secret.encode("utf-8")) < 32:
                raise RuntimeError("生产环境 JWT_SECRET 必须至少包含 32 个 UTF-8 字节")
            if len(self.agent_service_token.encode("utf-8")) < 32:
                raise RuntimeError("生产环境 AGENT_SERVICE_TOKEN 必须至少包含 32 个 UTF-8 字节")
            if self.checkpoint_backend != "redis":
                raise RuntimeError("生产环境 AGENT_CHECKPOINT_BACKEND 必须为 redis")
        if self.planner_mode not in {"auto", "heuristic", "llm"}:
            raise RuntimeError("AGENT_PLANNER_MODE 只能是 auto、heuristic 或 llm")
        if self.max_steps < 1 or self.max_steps > 20:
            raise RuntimeError("AGENT_MAX_STEPS 必须在 1 到 20 之间")
