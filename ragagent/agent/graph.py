from __future__ import annotations

from typing import Any
from uuid import uuid4

from langgraph.graph import END, START, StateGraph
from langgraph.runtime import Runtime
from langgraph.types import interrupt

from ragagent.agent.planner import AgentPlanner
from ragagent.agent.policies import requires_approval, validate_tool_call
from ragagent.agent.state import AgentContext, AgentState
from ragagent.agent.tools import AgentToolRegistry


class EnterpriseAgentGraph:
    def __init__(self, planner: AgentPlanner, tools: AgentToolRegistry):
        self._planner = planner
        self._tools = tools

    def compile(self, checkpointer: Any):
        builder = StateGraph(AgentState, context_schema=AgentContext)
        builder.add_node("validate_input", self._validate_input)
        builder.add_node("planner", self._plan)
        builder.add_node("approval", self._approval)
        builder.add_node("execute_read", self._execute_read)
        builder.add_node("execute_write", self._execute_write)
        builder.add_node("finalize", self._finalize)
        builder.add_edge(START, "validate_input")
        builder.add_edge("validate_input", "planner")
        builder.add_conditional_edges(
            "planner",
            self._route_after_plan,
            {"approval": "approval", "read": "execute_read", "final": "finalize"},
        )
        builder.add_conditional_edges(
            "approval",
            lambda state: state.get("next_action", "final"),
            {"write": "execute_write", "final": "finalize"},
        )
        builder.add_edge("execute_read", "planner")
        builder.add_edge("execute_write", "planner")
        builder.add_edge("finalize", END)
        return builder.compile(checkpointer=checkpointer)

    @staticmethod
    async def _validate_input(state: AgentState) -> dict:
        text = state.get("input_text", "").strip()
        if not text:
            raise ValueError("用户输入不能为空")
        return {"input_text": text, "status": "running", "step_count": 0, "tool_results": [], "citations": []}

    async def _plan(self, state: AgentState) -> dict:
        if state.get("step_count", 0) >= state.get("max_steps", 6):
            return {"final_answer": "已达到最大执行步数，操作已安全停止。", "next_action": "final"}
        decision = await self._planner.plan(state)
        if decision.action == "final":
            return {"intent": decision.intent, "final_answer": decision.answer, "next_action": "final"}
        assert decision.tool_name is not None
        validate_tool_call(decision.tool_name, decision.arguments)
        call = {
            "callId": str(uuid4()),
            "approvalId": str(uuid4()),
            "toolName": decision.tool_name,
            "arguments": decision.arguments,
            "intent": decision.intent,
        }
        return {
            "intent": decision.intent,
            "pending_tool_call": call,
            "next_action": "approval" if requires_approval(decision.tool_name) else "read",
        }

    @staticmethod
    def _route_after_plan(state: AgentState) -> str:
        return state.get("next_action", "final")

    @staticmethod
    async def _approval(state: AgentState) -> dict:
        call = state["pending_tool_call"]
        decision = interrupt(
            {
                "approvalId": call["approvalId"],
                "toolName": call["toolName"],
                "arguments": call["arguments"],
                "summary": f"即将执行写操作：{call['toolName']}",
            }
        )
        if not bool(decision.get("approved")):
            reason = decision.get("reason") or "用户未批准"
            return {
                "approval": decision,
                "pending_tool_call": None,
                "final_answer": f"操作已取消：{reason}",
                "next_action": "final",
            }
        return {"approval": decision, "next_action": "write"}

    async def _execute_read(self, state: AgentState, runtime: Runtime[AgentContext]) -> dict:
        return await self._execute(state, runtime.context.access_token)

    async def _execute_write(self, state: AgentState, runtime: Runtime[AgentContext]) -> dict:
        return await self._execute(state, runtime.context.access_token)

    async def _execute(self, state: AgentState, access_token: str) -> dict:
        call = state["pending_tool_call"]
        data = await self._tools.execute(
            call["toolName"],
            call["arguments"],
            access_token,
            call["callId"],
        )
        result = {"toolName": call["toolName"], "data": data, "intent": call.get("intent")}
        citations = state.get("citations", [])
        if call["toolName"] == "search_knowledge_base" and isinstance(data, list):
            citations = [
                {
                    "chunkId": item["chunkId"],
                    "source": item["source"],
                    "score": item["score"],
                    "excerpt": item["content"][:240],
                }
                for item in data
            ]
        return {
            "tool_results": [*state.get("tool_results", []), result],
            "citations": citations,
            "pending_tool_call": None,
            "step_count": state.get("step_count", 0) + 1,
            "next_action": "planner",
        }

    @staticmethod
    async def _finalize(state: AgentState) -> dict:
        return {"status": "completed", "final_answer": state.get("final_answer") or "任务已完成。"}
