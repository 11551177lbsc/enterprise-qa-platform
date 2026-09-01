from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from ragagent.agent.checkpoint import checkpoint_lifespan
from ragagent.agent.graph import EnterpriseAgentGraph
from ragagent.agent.planner import AgentPlanner
from ragagent.agent.service import AgentRunService
from ragagent.agent.tools import AgentToolRegistry
from ragagent.api.agent_routes import router as agent_router
from ragagent.api.rag_routes import router as rag_router
from ragagent.clients.java_tool_client import JavaToolClient
from ragagent.security.jwt_auth import JwtAuthenticator
from ragagent.settings import Settings
from ragagent.agent.events import RedisEventBroker
from ragagent.persistence.run_store import RedisRunStore


def _optional_chat_model(settings: Settings):
    if settings.planner_mode == "heuristic":
        return None
    try:
        from ragagent.model.factory import chat_model

        return chat_model
    except Exception:
        return None


def create_app(settings: Settings | None = None, planner=None, tools=None, feedback_client=None) -> FastAPI:
    settings = settings or Settings.from_env()
    settings.validate()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        async with checkpoint_lifespan(settings) as checkpointer:
            selected_planner = planner or AgentPlanner(settings, _optional_chat_model(settings))
            java_client = JavaToolClient(settings)
            selected_tools = tools or AgentToolRegistry(java_client)
            graph = EnterpriseAgentGraph(selected_planner, selected_tools).compile(checkpointer)
            redis_client = None
            run_store = None
            event_broker = None
            if settings.checkpoint_backend == "redis":
                from redis.asyncio import Redis

                redis_client = Redis.from_url(settings.redis_url, decode_responses=True)
                await redis_client.ping()
                run_store = RedisRunStore(redis_client, settings.run_ttl_seconds)
                event_broker = RedisEventBroker(redis_client, settings.run_ttl_seconds)
            app.state.agent_service = AgentRunService(
                graph,
                settings,
                store=run_store,
                events=event_broker,
                feedback_client=feedback_client or java_client,
            )
            app.state.authenticator = JwtAuthenticator(settings)
            app.state.settings = settings
            try:
                yield
            finally:
                if redis_client is not None:
                    await redis_client.aclose()

    app = FastAPI(title="Enterprise QA LangGraph Agent", version="2.0.0", lifespan=lifespan)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=list(settings.cors_origins),
        allow_credentials=True,
        allow_methods=["GET", "POST", "OPTIONS"],
        allow_headers=["Authorization", "Content-Type", "Last-Event-ID"],
    )
    app.include_router(agent_router)
    app.include_router(rag_router)

    @app.get("/health")
    async def health():
        return {"status": "ok", "service": "langgraph-agent", "checkpoint": settings.checkpoint_backend}

    return app


app = create_app()


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("ragagent.main:app", host="0.0.0.0", port=8001, reload=False)
