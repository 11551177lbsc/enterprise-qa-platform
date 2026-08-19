from __future__ import annotations

import json
import os
import re
from typing import Any, Literal

from langchain_core.messages import HumanMessage, SystemMessage
from pydantic import BaseModel, Field

from ragagent.agent.prompts import PLANNER_SYSTEM_PROMPT
from ragagent.agent.state import AgentState
from ragagent.settings import Settings


class PlanningDecision(BaseModel):
    action: Literal["tool", "final"]
    tool_name: str | None = None
    arguments: dict[str, Any] = Field(default_factory=dict)
    answer: str | None = None
    intent: str = "knowledge_qa"


class AgentPlanner:
    def __init__(self, settings: Settings, model: Any | None = None):
        self._settings = settings
        self._model = model

    async def plan(self, state: AgentState) -> PlanningDecision:
        results = state.get("tool_results", [])
        if results:
            return self._answer_from_result(results[-1])

        if self._should_use_llm():
            try:
                return await self._llm_plan(state["input_text"])
            except Exception:
                # 模型不可用时仍保持一个可测试、可降级的确定性执行路径。
                pass
        return self._heuristic_plan(state["input_text"])

    def _should_use_llm(self) -> bool:
        if self._settings.planner_mode == "heuristic":
            return False
        return self._model is not None and bool(os.getenv("DASHSCOPE_API_KEY") or os.getenv("LLM_API_KEY"))

    async def _llm_plan(self, text: str) -> PlanningDecision:
        structured = self._model.with_structured_output(PlanningDecision)
        return await structured.ainvoke(
            [
                SystemMessage(content=PLANNER_SYSTEM_PROMPT),
                HumanMessage(
                    content=(
                        "可用工具：search_knowledge_base、get_current_user_profile、"
                        "list_support_tickets、get_support_ticket、create_support_ticket、"
                        "update_support_ticket、send_ticket_notification。\n用户请求：" + text
                    )
                ),
            ]
        )

    @staticmethod
    def _heuristic_plan(text: str) -> PlanningDecision:
        normalized = text.strip()
        lower = normalized.lower()
        ticket_match = re.search(r"(?:工单|ticket)[#：:\s-]*(\d+)", normalized, re.IGNORECASE)

        if any(word in normalized for word in ("我的信息", "个人信息", "当前用户", "我的账号")):
            return PlanningDecision(action="tool", tool_name="get_current_user_profile", intent="profile")

        if "工单" in normalized or "ticket" in lower:
            if any(word in normalized for word in ("创建", "新建", "提交", "反馈")):
                return PlanningDecision(
                    action="tool",
                    tool_name="create_support_ticket",
                    arguments={"title": normalized[:120], "description": normalized, "priority": "MEDIUM"},
                    intent="ticket_write",
                )
            if any(word in normalized for word in ("通知", "提醒", "发送")) and ticket_match:
                return PlanningDecision(
                    action="tool",
                    tool_name="send_ticket_notification",
                    arguments={"ticketId": int(ticket_match.group(1)), "channel": "IN_APP"},
                    intent="ticket_write",
                )
            if any(word in normalized for word in ("更新", "修改", "关闭", "解决")) and ticket_match:
                status = "CLOSED" if "关闭" in normalized else "RESOLVED" if "解决" in normalized else "OPEN"
                return PlanningDecision(
                    action="tool",
                    tool_name="update_support_ticket",
                    arguments={"ticketId": int(ticket_match.group(1)), "status": status},
                    intent="ticket_write",
                )
            if ticket_match:
                return PlanningDecision(
                    action="tool",
                    tool_name="get_support_ticket",
                    arguments={"ticketId": int(ticket_match.group(1))},
                    intent="ticket_read",
                )
            return PlanningDecision(action="tool", tool_name="list_support_tickets", intent="ticket_read")

        return PlanningDecision(
            action="tool",
            tool_name="search_knowledge_base",
            arguments={"query": normalized, "topK": 5},
            intent="knowledge_qa",
        )

    @staticmethod
    def _answer_from_result(result: dict[str, Any]) -> PlanningDecision:
        tool = result.get("toolName", "工具")
        data = result.get("data")
        if tool == "search_knowledge_base":
            chunks = data if isinstance(data, list) else []
            if not chunks:
                answer = "知识库中暂未检索到足够相关的资料。你可以补充产品型号、故障现象或使用场景。"
            else:
                excerpts = [str(item.get("content", "")).strip() for item in chunks[:3] if item.get("content")]
                answer = "根据知识库资料：\n" + "\n".join(f"- {text}" for text in excerpts)
        elif tool == "get_current_user_profile":
            answer = "当前用户信息：" + json.dumps(data, ensure_ascii=False)
        elif tool == "list_support_tickets":
            answer = "你的工单列表：" + json.dumps(data, ensure_ascii=False)
        elif tool == "get_support_ticket":
            answer = "工单详情：" + json.dumps(data, ensure_ascii=False)
        else:
            answer = f"操作已完成（{tool}）：" + json.dumps(data, ensure_ascii=False)
        return PlanningDecision(action="final", answer=answer, intent=str(result.get("intent", "tool_result")))
