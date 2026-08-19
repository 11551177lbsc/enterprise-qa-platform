from __future__ import annotations

from typing import Any

import httpx

from ragagent.settings import Settings


class JavaToolError(RuntimeError):
    pass


class JavaToolClient:
    def __init__(self, settings: Settings, transport: httpx.AsyncBaseTransport | None = None):
        self._base_url = settings.java_base_url.rstrip("/")
        self._service_token = settings.agent_service_token
        self._timeout = settings.request_timeout_seconds
        self._transport = transport

    async def execute(
        self,
        tool_name: str,
        arguments: dict[str, Any],
        access_token: str,
        invocation_id: str,
    ) -> Any:
        method, path, body = self._request_spec(tool_name, arguments)
        headers = {
            "Authorization": f"Bearer {access_token}",
            "X-Agent-Service-Token": self._service_token,
            "X-Agent-Tool-Name": tool_name,
            "X-Agent-Invocation-Id": invocation_id,
        }
        async with httpx.AsyncClient(
            base_url=self._base_url,
            timeout=self._timeout,
            transport=self._transport,
        ) as client:
            response = await client.request(method, path, headers=headers, json=body)
        if response.status_code >= 400:
            raise JavaToolError(f"Java 工具网关调用失败：HTTP {response.status_code}")
        payload = response.json()
        if isinstance(payload, dict) and payload.get("success") is False:
            raise JavaToolError(str(payload.get("message") or "Java 工具执行失败"))
        return payload.get("data", payload) if isinstance(payload, dict) else payload

    @staticmethod
    def _request_spec(tool_name: str, args: dict[str, Any]) -> tuple[str, str, dict | None]:
        if tool_name == "get_current_user_profile":
            return "GET", "/internal/agent-tools/users/me", None
        if tool_name == "list_support_tickets":
            return "GET", "/internal/agent-tools/tickets", None
        if tool_name == "get_support_ticket":
            return "GET", f"/internal/agent-tools/tickets/{int(args['ticketId'])}", None
        if tool_name == "create_support_ticket":
            return "POST", "/internal/agent-tools/tickets", args
        if tool_name == "update_support_ticket":
            ticket_id = int(args["ticketId"])
            body = {key: value for key, value in args.items() if key != "ticketId"}
            return "PATCH", f"/internal/agent-tools/tickets/{ticket_id}", body
        if tool_name == "send_ticket_notification":
            return "POST", "/internal/agent-tools/notifications", args
        raise ValueError(f"未知 Java 工具：{tool_name}")
