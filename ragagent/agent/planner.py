from __future__ import annotations

import json
import os
import re
from typing import Any, Literal

from langchain_core.messages import HumanMessage, SystemMessage
from pydantic import BaseModel, Field

from ragagent.agent.prompts import PLANNER_SYSTEM_PROMPT
from ragagent.agent.state import AgentState
from ragagent.schemas.agent import ResolutionOutcome, ResolutionSummary, TicketDraft
from ragagent.settings import Settings


class PlanningDecision(BaseModel):
    action: Literal["tool", "final"]
    tool_name: str | None = None
    arguments: dict[str, Any] = Field(default_factory=dict)
    answer: str | None = None
    intent: str = "knowledge_qa"
    resolution: ResolutionSummary | None = None


class GroundedKnowledgeAnswer(BaseModel):
    answer: str = Field(min_length=1, max_length=4000)
    next_steps: list[str] = Field(default_factory=list, max_length=5)


class AgentPlanner:
    def __init__(self, settings: Settings, model: Any | None = None):
        self._settings = settings
        self._model = model

    async def plan(self, state: AgentState) -> PlanningDecision:
        results = state.get("tool_results", [])
        if results:
            return await self._answer_from_result(results[-1], state.get("input_text", ""))

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
                title, description, priority = AgentPlanner._parse_ticket_request(normalized)
                return PlanningDecision(
                    action="tool",
                    tool_name="create_support_ticket",
                    arguments={"title": title, "description": description, "priority": priority},
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

    async def _answer_from_result(self, result: dict[str, Any], question: str) -> PlanningDecision:
        tool = result.get("toolName", "工具")
        data = result.get("data")
        if tool == "search_knowledge_base":
            return await self._answer_from_knowledge(question, data if isinstance(data, list) else [])
        elif tool == "get_current_user_profile":
            answer = self._format_profile(data)
        elif tool == "list_support_tickets":
            answer = self._format_ticket_list(data)
        elif tool == "get_support_ticket":
            answer = self._format_ticket(data)
        elif tool in {"create_support_ticket", "update_support_ticket"}:
            answer = "操作已完成。\n\n" + self._format_ticket(data)
        else:
            answer = f"操作已完成（{tool}）：" + json.dumps(data, ensure_ascii=False)
        return PlanningDecision(
            action="final",
            answer=answer,
            intent=str(result.get("intent", "tool_result")),
            resolution=ResolutionSummary(
                outcome=ResolutionOutcome.ACTION_COMPLETED,
                confidence=1.0,
                reason="业务系统已返回可核验的执行结果。",
                nextSteps=self._business_next_steps(tool, data),
            ),
        )

    async def _answer_from_knowledge(self, question: str, chunks: list[dict[str, Any]]) -> PlanningDecision:
        ranked = sorted(chunks, key=lambda item: float(item.get("score", 0)), reverse=True)
        confidence = max(0.0, min(1.0, float(ranked[0].get("score", 0)))) if ranked else 0.0
        threshold = self._settings.rag_confidence_threshold

        if not ranked or confidence < threshold:
            sources = sorted({str(item.get("source", "unknown")) for item in ranked[:3]})
            evidence_note = "、".join(sources) if sources else "无"
            answer = (
                "当前知识库没有检索到足够可靠的依据，我不会直接生成可能误导你的答案。"
                "你可以补充产品型号、故障现象、指示灯状态和已经尝试过的步骤，"
                "也可以将本次问题升级为支持工单。"
            )
            draft = self._ticket_draft(question, confidence, evidence_note)
            return PlanningDecision(
                action="final",
                answer=answer,
                intent="knowledge_escalation",
                resolution=ResolutionSummary(
                    outcome=ResolutionOutcome.ESCALATION_RECOMMENDED,
                    confidence=round(confidence, 4),
                    reason=f"最佳知识匹配度 {confidence:.2f}，低于安全阈值 {threshold:.2f}。",
                    nextSteps=[
                        "补充产品型号和可复现的故障现象",
                        "确认工单草稿后提交给人工支持",
                    ],
                    ticketDraft=draft,
                ),
            )

        fallback_steps = [str(item.get("content", "")).strip() for item in ranked[:3] if item.get("content")]
        answer = "根据知识库资料：\n" + "\n".join(f"- {text}" for text in fallback_steps)
        next_steps = ["按知识库步骤逐项排查", "若问题仍未解决，可升级为支持工单"]

        if self._should_use_llm():
            try:
                grounded = await self._synthesize_grounded_answer(question, ranked[:3])
                answer = grounded.answer
                if grounded.next_steps:
                    next_steps = grounded.next_steps[:5]
            except Exception:
                # 模型总结失败时仍返回原始知识证据，不影响核心业务流程。
                pass

        return PlanningDecision(
            action="final",
            answer=answer,
            intent="knowledge_answered",
            resolution=ResolutionSummary(
                outcome=ResolutionOutcome.ANSWERED,
                confidence=round(confidence, 4),
                reason=f"知识库已命中高于安全阈值 {threshold:.2f} 的资料。",
                nextSteps=next_steps,
            ),
        )

    async def _synthesize_grounded_answer(
        self,
        question: str,
        chunks: list[dict[str, Any]],
    ) -> GroundedKnowledgeAnswer:
        structured = self._model.with_structured_output(GroundedKnowledgeAnswer)
        evidence = [
            {
                "source": item.get("source", "unknown"),
                "score": item.get("score", 0),
                "content": str(item.get("content", ""))[:1600],
            }
            for item in chunks
        ]
        return await structured.ainvoke(
            [
                SystemMessage(
                    content=(
                        "你是企业支持知识助手。只能依据给定知识片段回答，不得补造产品事实。"
                        "回答应先给结论，再列出可执行排查步骤；无法从证据确认的内容要明确说明。"
                    )
                ),
                HumanMessage(
                    content="用户问题："
                    + question
                    + "\n知识证据："
                    + json.dumps(evidence, ensure_ascii=False)
                ),
            ]
        )

    @staticmethod
    def _ticket_draft(question: str, confidence: float, sources: str) -> TicketDraft:
        cleaned = question.strip() or "知识库未解决的问题"
        title = re.sub(r"[？?。！!]+$", "", cleaned)[:120]
        priority = AgentPlanner._infer_priority(cleaned)
        description = (
            f"用户问题：{cleaned}\n"
            f"自动分流原因：知识库证据不足（最佳匹配度 {confidence:.2f}）。\n"
            f"相关资料：{sources}\n"
            "请人工支持进一步诊断；提交前用户可补充型号、故障现象和已尝试步骤。"
        )
        return TicketDraft(title=title, description=description, priority=priority)

    @staticmethod
    def _parse_ticket_request(text: str) -> tuple[str, str, str]:
        title_match = re.search(r"标题[：:]\s*([^\n；;]+)", text)
        description_match = re.search(r"描述[：:]\s*(.+)", text, re.DOTALL)
        priority_match = re.search(r"优先级[：:]\s*(LOW|MEDIUM|HIGH|URGENT)", text, re.IGNORECASE)
        fallback_title = re.sub(r"^.*?(?:创建|新建|提交|反馈)(?:一个|一条)?(?:支持|售后)?工单[：:\s]*", "", text)
        title = (title_match.group(1) if title_match else fallback_title).strip() or "用户支持请求"
        description = (description_match.group(1) if description_match else text).strip()
        priority = priority_match.group(1).upper() if priority_match else AgentPlanner._infer_priority(text)
        return title[:120], description[:4000], priority

    @staticmethod
    def _infer_priority(text: str) -> str:
        if any(word in text for word in ("起火", "冒烟", "爆炸", "触电", "人身伤害", "安全事故")):
            return "URGENT"
        if any(word in text for word in ("无法开机", "无法充电", "无法回充", "漏水", "异常高温", "完全无法使用")):
            return "HIGH"
        return "MEDIUM"

    @staticmethod
    def _business_next_steps(tool: str, data: Any) -> list[str]:
        if tool == "create_support_ticket":
            return ["在“我的工单”中跟踪处理状态", "补充新的排查信息时更新工单"]
        if tool == "update_support_ticket":
            return ["确认工单状态和修改内容是否符合预期"]
        if tool == "list_support_tickets":
            return ["选择具体工单查看详情或继续处理"]
        return []

    @staticmethod
    def _format_profile(data: Any) -> str:
        if not isinstance(data, dict):
            return "当前用户信息暂不可用。"
        return (
            "### 当前账号\n\n"
            f"- 用户名：{data.get('username') or '-'}\n"
            f"- 邮箱：{data.get('email') or '未填写'}\n"
            f"- 用户 ID：{data.get('id') or data.get('userId') or '-'}"
        )

    @staticmethod
    def _format_ticket_list(data: Any) -> str:
        tickets = data if isinstance(data, list) else []
        if not tickets:
            return "当前账号还没有支持工单。"
        lines = [
            "### 我的工单",
            "",
            "| 工单 | 标题 | 优先级 | 状态 | 更新时间 |",
            "| --- | --- | --- | --- | --- |",
        ]
        for item in tickets:
            title = str(item.get("title") or "-").replace("|", "\\|").replace("\n", " ")
            lines.append(
                f"| #{item.get('id', '-')} | {title} | {item.get('priority', '-')} | "
                f"{item.get('status', '-')} | {item.get('updatedAt') or '-'} |"
            )
        return "\n".join(lines)

    @staticmethod
    def _format_ticket(data: Any) -> str:
        if not isinstance(data, dict):
            return "工单详情暂不可用。"
        return (
            f"### 工单 #{data.get('id', '-')}\n\n"
            f"- 标题：{data.get('title') or '-'}\n"
            f"- 优先级：{data.get('priority') or '-'}\n"
            f"- 状态：{data.get('status') or '-'}\n"
            f"- 更新时间：{data.get('updatedAt') or '-'}\n\n"
            f"**问题描述**\n\n{data.get('description') or '-'}"
        )
