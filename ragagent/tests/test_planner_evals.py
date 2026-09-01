import json
from pathlib import Path

import pytest

from ragagent.agent.planner import AgentPlanner
from ragagent.agent.policies import requires_approval


CASES = json.loads((Path(__file__).parents[1] / "evals" / "cases.json").read_text(encoding="utf-8"))


@pytest.mark.asyncio
@pytest.mark.parametrize("case", CASES, ids=[item["expected_tool"] for item in CASES])
async def test_planner_policy_dataset(settings, case):
    decision = await AgentPlanner(settings).plan({"input_text": case["input"], "tool_results": []})
    assert decision.tool_name == case["expected_tool"]
    assert requires_approval(decision.tool_name) is case["approval"]


@pytest.mark.asyncio
async def test_escalated_ticket_prompt_preserves_structured_fields(settings):
    decision = await AgentPlanner(settings).plan(
        {
            "input_text": (
                "请创建支持工单：\n"
                "标题：充电座出现冒烟\n"
                "优先级：URGENT\n"
                "描述：充电座通电后冒烟，已经立即断电"
            ),
            "tool_results": [],
        }
    )

    assert decision.tool_name == "create_support_ticket"
    assert decision.arguments["title"] == "充电座出现冒烟"
    assert decision.arguments["priority"] == "URGENT"
    assert decision.arguments["category"] == "SAFETY"
    assert decision.arguments["description"] == "充电座通电后冒烟，已经立即断电"


@pytest.mark.asyncio
async def test_ticket_list_is_formatted_for_people_instead_of_raw_json(settings):
    decision = await AgentPlanner(settings).plan(
        {
            "input_text": "列出我的工单",
            "tool_results": [
                {
                    "toolName": "list_support_tickets",
                    "intent": "ticket_read",
                    "data": [
                        {
                            "id": 42,
                            "title": "机器无法回充",
                            "priority": "HIGH",
                            "status": "OPEN",
                            "updatedAt": "2026-09-01T23:00:00",
                        }
                    ],
                }
            ],
        }
    )

    assert "| #42 | 机器无法回充 | HIGH | OPEN |" in decision.answer
    assert decision.resolution.outcome == "action_completed"


def test_retrieval_eval_dataset_has_valid_sources():
    path = Path(__file__).parents[1] / "evals" / "retrieval_cases.json"
    cases = json.loads(path.read_text(encoding="utf-8"))

    assert len(cases) >= 4
    assert all(case["question"].strip() for case in cases)
    assert all(case["expected_sources"] for case in cases)
