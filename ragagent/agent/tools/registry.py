from __future__ import annotations

from typing import Any

from ragagent.agent.policies import validate_tool_call
from ragagent.agent.tools.knowledge import KnowledgeSearchTool
from ragagent.clients.java_tool_client import JavaToolClient


class AgentToolRegistry:
    def __init__(self, java_client: JavaToolClient, knowledge_tool: KnowledgeSearchTool | None = None):
        self._java_client = java_client
        self._knowledge = knowledge_tool or KnowledgeSearchTool()

    async def execute(
        self,
        tool_name: str,
        arguments: dict[str, Any],
        access_token: str,
        invocation_id: str,
    ) -> Any:
        validate_tool_call(tool_name, arguments)
        if tool_name == "search_knowledge_base":
            return await self._knowledge.search(str(arguments["query"]), int(arguments.get("topK", 5)))
        return await self._java_client.execute(tool_name, arguments, access_token, invocation_id)
