import pytest
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.types import Command

from ragagent.agent.graph import EnterpriseAgentGraph
from ragagent.agent.planner import AgentPlanner
from ragagent.agent.state import AgentContext


class FakeTools:
    def __init__(self):
        self.calls = []

    async def execute(self, tool_name, arguments, access_token, invocation_id):
        self.calls.append((tool_name, arguments, access_token, invocation_id))
        if tool_name == "search_knowledge_base":
            return [
                {
                    "chunkId": "chunk-1",
                    "source": "维护保养.txt",
                    "score": 0.91,
                    "content": "过滤网应定期清理，以免影响吸力。",
                }
            ]
        if tool_name == "create_support_ticket":
            return {"id": 42, "status": "OPEN", "title": arguments["title"]}
        return {}


def compile_graph(settings):
    tools = FakeTools()
    planner = AgentPlanner(settings)
    graph = EnterpriseAgentGraph(planner, tools).compile(InMemorySaver())
    return graph, tools


@pytest.mark.asyncio
async def test_read_tool_executes_without_approval(settings):
    graph, tools = compile_graph(settings)
    result = await graph.ainvoke(
        {
            "input_text": "扫地机器人吸力变弱怎么办？",
            "run_id": "run-read",
            "thread_id": "thread-read",
            "user_id": 1,
            "max_steps": 6,
        },
        config={"configurable": {"thread_id": "thread-read"}},
        context=AgentContext(access_token="jwt-read"),
    )

    assert result["status"] == "completed"
    assert "过滤网" in result["final_answer"]
    assert result["citations"][0]["source"] == "维护保养.txt"
    assert result["resolution"]["outcome"] == "answered"
    assert result["resolution"]["confidence"] == 0.91
    assert tools.calls[0][2] == "jwt-read"


@pytest.mark.asyncio
async def test_low_confidence_knowledge_recommends_ticket_without_writing(settings):
    class LowConfidenceTools(FakeTools):
        async def execute(self, tool_name, arguments, access_token, invocation_id):
            self.calls.append((tool_name, arguments, access_token, invocation_id))
            return [
                {
                    "chunkId": "weak-chunk",
                    "source": "维护保养.txt",
                    "score": 0.41,
                    "content": "这是一个与问题关联较弱的片段。",
                }
            ]

    tools = LowConfidenceTools()
    planner = AgentPlanner(settings)
    graph = EnterpriseAgentGraph(planner, tools).compile(InMemorySaver())
    result = await graph.ainvoke(
        {
            "input_text": "公司的年假怎么申请？",
            "run_id": "run-low-confidence",
            "thread_id": "thread-low-confidence",
            "user_id": 1,
            "max_steps": 6,
        },
        config={"configurable": {"thread_id": "thread-low-confidence"}},
        context=AgentContext(access_token="jwt-read"),
    )

    resolution = result["resolution"]
    assert resolution["outcome"] == "escalation_recommended"
    assert resolution["ticketDraft"]["title"] == "公司的年假怎么申请"
    assert resolution["ticketDraft"]["priority"] == "MEDIUM"
    assert len(tools.calls) == 1


@pytest.mark.asyncio
async def test_write_tool_waits_for_approval_then_resumes(settings):
    graph, tools = compile_graph(settings)
    config = {"configurable": {"thread_id": "thread-write"}}
    first = await graph.ainvoke(
        {
            "input_text": "请创建工单：机器无法回充",
            "run_id": "run-write",
            "thread_id": "thread-write",
            "user_id": 1,
            "max_steps": 6,
        },
        config=config,
        context=AgentContext(access_token="jwt-write"),
    )

    assert first["__interrupt__"]
    assert first["__interrupt__"][0].value["toolName"] == "create_support_ticket"
    assert tools.calls == []

    resumed = await graph.ainvoke(
        Command(resume={"approved": True, "reason": "确认"}),
        config=config,
        context=AgentContext(access_token="jwt-write"),
    )
    assert resumed["status"] == "completed"
    assert resumed["tool_results"][0]["data"]["id"] == 42
    assert resumed["resolution"]["outcome"] == "action_completed"
    assert len(tools.calls) == 1


@pytest.mark.asyncio
async def test_rejected_write_never_calls_tool(settings):
    graph, tools = compile_graph(settings)
    config = {"configurable": {"thread_id": "thread-reject"}}
    await graph.ainvoke(
        {
            "input_text": "创建一个售后工单",
            "run_id": "run-reject",
            "thread_id": "thread-reject",
            "user_id": 1,
            "max_steps": 6,
        },
        config=config,
        context=AgentContext(access_token="jwt"),
    )
    result = await graph.ainvoke(
        Command(resume={"approved": False, "reason": "暂不创建"}),
        config=config,
        context=AgentContext(access_token="jwt"),
    )
    assert result["status"] == "completed"
    assert "暂不创建" in result["final_answer"]
    assert tools.calls == []
