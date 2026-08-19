from __future__ import annotations

import asyncio
from redis.asyncio import Redis

from ragagent.schemas.agent import AgentRun, RunStatus, utc_now


class MemoryRunStore:
    def __init__(self):
        self._runs: dict[str, AgentRun] = {}
        self._lock = asyncio.Lock()

    async def create(self, run: AgentRun) -> AgentRun:
        async with self._lock:
            self._runs[run.runId] = run.model_copy(deep=True)
        return run

    async def get(self, run_id: str) -> AgentRun | None:
        async with self._lock:
            run = self._runs.get(run_id)
            return run.model_copy(deep=True) if run else None

    async def update(self, run_id: str, **changes) -> AgentRun:
        async with self._lock:
            current = self._runs[run_id]
            changes["updatedAt"] = utc_now()
            updated = current.model_copy(update=changes, deep=True)
            self._runs[run_id] = updated
            return updated.model_copy(deep=True)

    async def cancel(self, run_id: str) -> AgentRun:
        return await self.update(run_id, status=RunStatus.CANCELLED, pendingApproval=None)


class RedisRunStore:
    def __init__(self, redis: Redis, ttl_seconds: int):
        self._redis = redis
        self._ttl = ttl_seconds

    @staticmethod
    def _key(run_id: str) -> str:
        return f"agent:run:{run_id}"

    async def create(self, run: AgentRun) -> AgentRun:
        created = await self._redis.set(
            self._key(run.runId),
            run.model_dump_json(),
            ex=self._ttl,
            nx=True,
        )
        if not created:
            raise RuntimeError("runId 冲突")
        return run

    async def get(self, run_id: str) -> AgentRun | None:
        value = await self._redis.get(self._key(run_id))
        return AgentRun.model_validate_json(value) if value else None

    async def update(self, run_id: str, **changes) -> AgentRun:
        current = await self.get(run_id)
        if current is None:
            raise KeyError(run_id)
        changes["updatedAt"] = utc_now()
        updated = current.model_copy(update=changes, deep=True)
        await self._redis.set(self._key(run_id), updated.model_dump_json(), ex=self._ttl)
        return updated

    async def cancel(self, run_id: str) -> AgentRun:
        return await self.update(run_id, status=RunStatus.CANCELLED, pendingApproval=None)
