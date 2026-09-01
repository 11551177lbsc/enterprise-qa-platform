from __future__ import annotations

import asyncio
from uuid import uuid4

from langgraph.types import Command

from ragagent.agent.events import EventBroker
from ragagent.agent.state import AgentContext
from ragagent.persistence.run_store import MemoryRunStore
from ragagent.schemas.agent import (
    AgentEvent,
    AgentRun,
    AgentRunAccepted,
    AgentRunRequest,
    ApprovalRequest,
    Citation,
    PendingApproval,
    RunStatus,
    ResolutionSummary,
)
from ragagent.security.jwt_auth import AuthenticatedUser
from ragagent.settings import Settings


class AgentRunNotFound(KeyError):
    pass


class ApprovalConflict(RuntimeError):
    pass


class AgentRunService:
    def __init__(
        self,
        graph,
        settings: Settings,
        store=None,
        events=None,
    ):
        self.graph = graph
        self.settings = settings
        self.store = store or MemoryRunStore()
        self.events = events or EventBroker()
        self._tasks: dict[str, asyncio.Task] = {}

    async def start(self, request: AgentRunRequest, user: AuthenticatedUser) -> AgentRunAccepted:
        run_id = str(uuid4())
        thread_id = request.threadId or str(uuid4())
        run = AgentRun(
            runId=run_id,
            threadId=thread_id,
            userId=user.user_id,
            status=RunStatus.QUEUED,
            input=request.message,
        )
        await self.store.create(run)
        await self._emit(run_id, "run.queued", {"threadId": thread_id})
        task = asyncio.create_task(self._execute_initial(run, user.token), name=f"agent-run-{run_id}")
        self._tasks[run_id] = task
        task.add_done_callback(lambda _: self._tasks.pop(run_id, None))
        return AgentRunAccepted(runId=run_id, threadId=thread_id, status=RunStatus.QUEUED)

    async def get(self, run_id: str, user_id: int) -> AgentRun:
        run = await self.store.get(run_id)
        if run is None or run.userId != user_id:
            raise AgentRunNotFound(run_id)
        return run

    async def approve(
        self,
        run_id: str,
        approval_id: str,
        request: ApprovalRequest,
        user: AuthenticatedUser,
    ) -> AgentRunAccepted:
        run = await self.get(run_id, user.user_id)
        if run.status != RunStatus.WAITING_APPROVAL or not run.pendingApproval:
            raise ApprovalConflict("当前运行不在待审批状态")
        if run.pendingApproval.approvalId != approval_id:
            raise ApprovalConflict("approvalId 不匹配")
        run = await self.store.update(run_id, status=RunStatus.RUNNING)
        await self._emit(run_id, "approval.decided", request.model_dump())
        task = asyncio.create_task(
            self._resume(run, user.token, request),
            name=f"agent-resume-{run_id}",
        )
        self._tasks[run_id] = task
        task.add_done_callback(lambda _: self._tasks.pop(run_id, None))
        return AgentRunAccepted(runId=run_id, threadId=run.threadId, status=RunStatus.RUNNING)

    async def cancel(self, run_id: str, user_id: int) -> AgentRun:
        run = await self.get(run_id, user_id)
        task = self._tasks.get(run_id)
        if task and not task.done():
            task.cancel()
        cancelled = await self.store.cancel(run_id)
        await self._emit(run_id, "run.cancelled", {})
        return cancelled

    async def _execute_initial(self, run: AgentRun, access_token: str) -> None:
        await self.store.update(run.runId, status=RunStatus.RUNNING)
        await self._emit(run.runId, "run.started", {})
        state = {
            "run_id": run.runId,
            "thread_id": run.threadId,
            "user_id": run.userId,
            "input_text": run.input,
            "max_steps": self.settings.max_steps,
        }
        await self._invoke(run, state, access_token)

    async def _resume(self, run: AgentRun, access_token: str, approval: ApprovalRequest) -> None:
        await self._invoke(run, Command(resume=approval.model_dump()), access_token)

    async def _invoke(self, run: AgentRun, graph_input, access_token: str) -> None:
        config = {"configurable": {"thread_id": run.threadId}, "recursion_limit": self.settings.max_steps * 4}
        try:
            result = await self.graph.ainvoke(
                graph_input,
                config=config,
                context=AgentContext(access_token=access_token),
            )
            interrupts = result.get("__interrupt__", ())
            if interrupts:
                value = interrupts[0].value
                pending = PendingApproval.model_validate(value)
                await self.store.update(
                    run.runId,
                    status=RunStatus.WAITING_APPROVAL,
                    pendingApproval=pending,
                )
                await self._emit(run.runId, "approval.required", pending.model_dump())
                return

            citations = [Citation.model_validate(item) for item in result.get("citations", [])]
            raw_resolution = result.get("resolution")
            resolution = ResolutionSummary.model_validate(raw_resolution) if raw_resolution else None
            await self.store.update(
                run.runId,
                status=RunStatus.COMPLETED,
                answer=result.get("final_answer"),
                citations=citations,
                resolution=resolution,
                pendingApproval=None,
            )
            await self._emit(run.runId, "run.completed", {"answer": result.get("final_answer")})
        except asyncio.CancelledError:
            raise
        except Exception as exc:
            await self.store.update(run.runId, status=RunStatus.FAILED, error=str(exc), pendingApproval=None)
            await self._emit(run.runId, "run.failed", {"message": str(exc)})

    async def _emit(self, run_id: str, event_type: str, data: dict) -> None:
        await self.events.publish(AgentEvent(runId=run_id, type=event_type, data=data))
