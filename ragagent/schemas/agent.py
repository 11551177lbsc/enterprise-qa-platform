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


class ResolutionOutcome(StrEnum):
    ANSWERED = "answered"
    NEEDS_CLARIFICATION = "needs_clarification"
    ESCALATION_RECOMMENDED = "escalation_recommended"
    ACTION_COMPLETED = "action_completed"


class AgentRunRequest(BaseModel):
    message: str = Field(min_length=1, max_length=4000)
    threadId: str | None = Field(default=None, min_length=1, max_length=100)


class Citation(BaseModel):
    chunkId: str
    source: str
    score: float
    excerpt: str


class TicketDraft(BaseModel):
    title: str = Field(min_length=1, max_length=120)
    description: str = Field(min_length=1, max_length=4000)
    priority: str = Field(pattern="^(LOW|MEDIUM|HIGH|URGENT)$")
    category: str = Field(default="OTHER", pattern="^(DEVICE|ACCOUNT|ORDER|BILLING|SAFETY|OTHER)$")
    productModel: str | None = Field(default=None, max_length=120)
    knowledgeConfidence: float | None = Field(default=None, ge=0, le=1)
    escalationReason: str | None = Field(default=None, max_length=500)


class ResolutionSummary(BaseModel):
    """面向业务页面的结构化处理结论，而不是模型隐藏推理。"""

    outcome: ResolutionOutcome
    confidence: float = Field(ge=0, le=1)
    reason: str = Field(min_length=1, max_length=500)
    nextSteps: list[str] = Field(default_factory=list, max_length=5)
    ticketDraft: TicketDraft | None = None


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
    resolution: ResolutionSummary | None = None
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


class FeedbackOutcome(StrEnum):
    RESOLVED = "RESOLVED"
    UNRESOLVED = "UNRESOLVED"


class RunFeedbackRequest(BaseModel):
    outcome: FeedbackOutcome
    comment: str | None = Field(default=None, max_length=1000)


class AgentEvent(BaseModel):
    eventId: str = Field(default_factory=lambda: str(uuid4()))
    runId: str
    type: str
    data: dict[str, Any] = Field(default_factory=dict)
    createdAt: datetime = Field(default_factory=utc_now)
