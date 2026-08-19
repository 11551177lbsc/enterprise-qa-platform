from __future__ import annotations

import json

from fastapi import APIRouter, Depends, HTTPException, Request, status
from fastapi.responses import StreamingResponse

from ragagent.agent.service import AgentRunNotFound, AgentRunService, ApprovalConflict
from ragagent.schemas.agent import AgentRun, AgentRunAccepted, AgentRunRequest, ApprovalRequest, RunStatus

router = APIRouter(prefix="/api/agent", tags=["agent"])


def service_from(request: Request) -> AgentRunService:
    return request.app.state.agent_service


def current_user(request: Request):
    authorization = request.headers.get("Authorization")
    return request.app.state.authenticator.authenticate(authorization)


@router.post("/runs", response_model=AgentRunAccepted, status_code=status.HTTP_202_ACCEPTED)
async def create_run(
    payload: AgentRunRequest,
    user=Depends(current_user),
    service: AgentRunService = Depends(service_from),
):
    return await service.start(payload, user)


@router.get("/runs/{run_id}", response_model=AgentRun)
async def get_run(run_id: str, user=Depends(current_user), service: AgentRunService = Depends(service_from)):
    try:
        return await service.get(run_id, user.user_id)
    except AgentRunNotFound as exc:
        raise HTTPException(status_code=404, detail="运行不存在") from exc


@router.post("/runs/{run_id}/approvals/{approval_id}", response_model=AgentRunAccepted)
async def decide_approval(
    run_id: str,
    approval_id: str,
    payload: ApprovalRequest,
    user=Depends(current_user),
    service: AgentRunService = Depends(service_from),
):
    try:
        return await service.approve(run_id, approval_id, payload, user)
    except AgentRunNotFound as exc:
        raise HTTPException(status_code=404, detail="运行不存在") from exc
    except ApprovalConflict as exc:
        raise HTTPException(status_code=409, detail=str(exc)) from exc


@router.post("/runs/{run_id}/cancel", response_model=AgentRun)
async def cancel_run(run_id: str, user=Depends(current_user), service: AgentRunService = Depends(service_from)):
    try:
        return await service.cancel(run_id, user.user_id)
    except AgentRunNotFound as exc:
        raise HTTPException(status_code=404, detail="运行不存在") from exc


@router.get("/runs/{run_id}/events")
async def stream_events(run_id: str, user=Depends(current_user), service: AgentRunService = Depends(service_from)):
    await service.get(run_id, user.user_id)

    async def event_stream():
        async for event in service.events.stream(run_id):
            payload = event.model_dump(mode="json")
            yield f"id: {event.eventId}\nevent: {event.type}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"
            if event.type in {"run.completed", "run.failed", "run.cancelled"}:
                break

    return StreamingResponse(event_stream(), media_type="text/event-stream")
