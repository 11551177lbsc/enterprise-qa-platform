from __future__ import annotations

READ_TOOLS = {
    "search_knowledge_base",
    "get_current_user_profile",
    "list_support_tickets",
    "get_support_ticket",
}

WRITE_TOOLS = {
    "create_support_ticket",
    "update_support_ticket",
    "send_ticket_notification",
}

ALL_TOOLS = READ_TOOLS | WRITE_TOOLS


def requires_approval(tool_name: str) -> bool:
    return tool_name in WRITE_TOOLS


def validate_tool_call(tool_name: str, arguments: dict) -> None:
    if tool_name not in ALL_TOOLS:
        raise ValueError(f"不允许调用工具：{tool_name}")
    if any(key.lower() in {"token", "authorization", "user_id", "userid"} for key in arguments):
        raise ValueError("工具参数不得携带认证信息或用户身份")
