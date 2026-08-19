from __future__ import annotations

from datetime import datetime, timezone
from enum import StrEnum
from typing import Any
from uuid import uuid4

from pydantic import BaseModel, Field


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


class RunStatus(StrEnum):
    QUEUED = "queued"
    RUNNING = "running"
    WAITING_APPROVAL = "waiting_approval"
    COMPLETED = "completed"
    FAILED = "failed"
    CANCELLED = "cancelled"


class AgentRunRequest(BaseModel):
    message: str = Field(min_length=1, max_length=4000)
    threadId: str | None = Field(default=None, min_length=1, max_length=100)


class Citation(BaseModel):
    chunkId: str
    source: str
    score: float
    excerpt: str


class PendingApproval(BaseModel):
    approvalId: str = Field(default_factory=lambda: str(uuid4()))
    toolName: str
    arguments: dict[str, Any]
    summary: str


class AgentRun(BaseModel):
    runId: str
    threadId: str
    userId: int
    status: RunStatus
    input: str
    answer: str | None = None
    citations: list[Citation] = Field(default_factory=list)
    pendingApproval: PendingApproval | None = None
    error: str | None = None
    createdAt: datetime = Field(default_factory=utc_now)
    updatedAt: datetime = Field(default_factory=utc_now)


class AgentRunAccepted(BaseModel):
    runId: str
    threadId: str
    status: RunStatus


class ApprovalRequest(BaseModel):
    approved: bool
    reason: str | None = Field(default=None, max_length=500)


class AgentEvent(BaseModel):
    eventId: str = Field(default_factory=lambda: str(uuid4()))
    runId: str
    type: str
    data: dict[str, Any] = Field(default_factory=dict)
    createdAt: datetime = Field(default_factory=utc_now)
