from __future__ import annotations

from dataclasses import dataclass
from typing import Annotated, Any, TypedDict

from langchain_core.messages import AnyMessage
from langgraph.graph.message import add_messages


class AgentState(TypedDict, total=False):
    messages: Annotated[list[AnyMessage], add_messages]
    run_id: str
    thread_id: str
    user_id: int
    input_text: str
    intent: str
    pending_tool_call: dict[str, Any] | None
    tool_results: list[dict[str, Any]]
    citations: list[dict[str, Any]]
    resolution: dict[str, Any] | None
    approval: dict[str, Any] | None
    step_count: int
    max_steps: int
    status: str
    final_answer: str | None
    error: str | None
    next_action: str


@dataclass(frozen=True)
class AgentContext:
    """单次调用上下文；访问令牌不会进入 checkpoint。"""

    access_token: str
