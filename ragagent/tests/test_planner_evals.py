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
