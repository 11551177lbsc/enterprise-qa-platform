from __future__ import annotations

import asyncio
from redis.asyncio import Redis
from redis.exceptions import WatchError

from ragagent.schemas.agent import AgentRun, RunStatus, utc_now


class RunStateConflict(RuntimeError):
    pass


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

    async def update(
        self,
        run_id: str,
        *,
        expected_statuses: set[RunStatus] | None = None,
        **changes,
    ) -> AgentRun:
        async with self._lock:
            current = self._runs[run_id]
            if expected_statuses is not None and current.status not in expected_statuses:
                raise RunStateConflict(f"运行状态 {current.status} 不允许本次更新")
            changes["updatedAt"] = utc_now()
            updated = current.model_copy(update=changes, deep=True)
            self._runs[run_id] = updated
            return updated.model_copy(deep=True)

    async def cancel(self, run_id: str) -> AgentRun:
        return await self.update(
            run_id,
            expected_statuses={RunStatus.QUEUED, RunStatus.RUNNING, RunStatus.WAITING_APPROVAL},
            status=RunStatus.CANCELLED,
            pendingApproval=None,
        )


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

    async def update(
        self,
        run_id: str,
        *,
        expected_statuses: set[RunStatus] | None = None,
        **changes,
    ) -> AgentRun:
        key = self._key(run_id)
        for _ in range(5):
            async with self._redis.pipeline(transaction=True) as pipeline:
                try:
                    await pipeline.watch(key)
                    value = await pipeline.get(key)
                    if value is None:
                        raise KeyError(run_id)
                    current = AgentRun.model_validate_json(value)
                    if expected_statuses is not None and current.status not in expected_statuses:
                        raise RunStateConflict(f"运行状态 {current.status} 不允许本次更新")
                    changes["updatedAt"] = utc_now()
                    updated = current.model_copy(update=changes, deep=True)
                    pipeline.multi()
                    pipeline.set(key, updated.model_dump_json(), ex=self._ttl)
                    await pipeline.execute()
                    return updated
                except WatchError:
                    continue
        raise RunStateConflict("运行状态并发更新冲突，请重试")

    async def cancel(self, run_id: str) -> AgentRun:
        return await self.update(
            run_id,
            expected_statuses={RunStatus.QUEUED, RunStatus.RUNNING, RunStatus.WAITING_APPROVAL},
            status=RunStatus.CANCELLED,
            pendingApproval=None,
        )
