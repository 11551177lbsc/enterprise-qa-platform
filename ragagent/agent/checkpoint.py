from __future__ import annotations

from contextlib import asynccontextmanager

from langgraph.checkpoint.memory import InMemorySaver

from ragagent.settings import Settings


@asynccontextmanager
async def checkpoint_lifespan(settings: Settings):
    if settings.checkpoint_backend == "memory":
        yield InMemorySaver()
        return
    if settings.checkpoint_backend != "redis":
        raise RuntimeError(f"未知 checkpoint backend：{settings.checkpoint_backend}")

    from langgraph.checkpoint.redis.aio import AsyncRedisSaver

    async with AsyncRedisSaver.from_conn_string(settings.redis_url) as saver:
        await saver.asetup()
        yield saver
