from __future__ import annotations

import asyncio
from collections import defaultdict
from collections.abc import AsyncIterator

from ragagent.schemas.agent import AgentEvent
from redis.asyncio import Redis


class EventBroker:
    def __init__(self):
        self._history: dict[str, list[AgentEvent]] = defaultdict(list)
        self._subscribers: dict[str, set[asyncio.Queue[AgentEvent]]] = defaultdict(set)
        self._lock = asyncio.Lock()

    async def publish(self, event: AgentEvent) -> None:
        async with self._lock:
            self._history[event.runId].append(event)
            queues = list(self._subscribers[event.runId])
        for queue in queues:
            await queue.put(event)

    async def stream(self, run_id: str) -> AsyncIterator[AgentEvent]:
        queue: asyncio.Queue[AgentEvent] = asyncio.Queue()
        async with self._lock:
            history = list(self._history.get(run_id, []))
            self._subscribers[run_id].add(queue)
        try:
            for event in history:
                yield event
            while True:
                yield await queue.get()
        finally:
            async with self._lock:
                self._subscribers[run_id].discard(queue)


class RedisEventBroker:
    def __init__(self, redis: Redis, ttl_seconds: int):
        self._redis = redis
        self._ttl = ttl_seconds

    @staticmethod
    def _key(run_id: str) -> str:
        return f"agent:events:{run_id}"

    async def publish(self, event: AgentEvent) -> None:
        key = self._key(event.runId)
        await self._redis.xadd(key, {"event": event.model_dump_json()}, maxlen=1000, approximate=True)
        await self._redis.expire(key, self._ttl)

    async def stream(self, run_id: str) -> AsyncIterator[AgentEvent]:
        key = self._key(run_id)
        last_id = "0-0"
        while True:
            batches = await self._redis.xread({key: last_id}, count=100, block=15000)
            if not batches:
                continue
            for _, messages in batches:
                for message_id, fields in messages:
                    last_id = message_id
                    payload = fields.get("event")
                    if payload:
                        yield AgentEvent.model_validate_json(payload)
