"""
工具监控和模型回调（基于 LangChain Callback 系统 + 工具包装器）
替代原本不存在的 langchain.agents.middleware API
"""
import functools
from typing import Any
from langchain_core.callbacks import BaseCallbackHandler
from langchain_core.tools import BaseTool
from utils.logger_handler import logger


class AgentCallbackHandler(BaseCallbackHandler):
    """统一的 Agent 回调处理器：工具监控 + 模型调用前日志"""

    def on_tool_start(
        self,
        serialized: dict[str, Any],
        input_str: str,
        **kwargs: Any,
    ) -> None:
        tool_name = serialized.get("name", "unknown")
        logger.info(f"[tool monitor] 执行工具：{tool_name}")
        logger.info(f"[tool monitor] 传入参数：{input_str}")

    def on_tool_end(
        self,
        output: str,
        **kwargs: Any,
    ) -> None:
        logger.info(f"[tool monitor] 工具调用成功")

    def on_tool_error(
        self,
        error: BaseException,
        **kwargs: Any,
    ) -> None:
        logger.error(f"工具调用失败，原因：{str(error)}")

    def on_chat_model_start(
        self,
        serialized: dict[str, Any],
        messages: list[list[Any]],
        **kwargs: Any,
    ) -> None:
        msg_count = sum(len(inner) for inner in messages)
        logger.info(f"[log_before_model] 即将调用模型，带有 {msg_count} 条消息")
        for inner in messages:
            if inner:
                last = inner[-1]
                if hasattr(last, "content"):
                    logger.debug(f"[log_before_model] {type(last).__name__} | {str(last.content)[:200]}")


# 全局回调处理器实例
agent_callback = AgentCallbackHandler()


def wrap_tools_with_report_flag(
    tools: list[BaseTool],
    report_flag_holder: dict,
) -> list[BaseTool]:
    """
    包装工具列表：当 fill_context_for_report 被调用时，
    自动设置 report_flag_holder["report"] = True，
    用于触发后续提示词动态切换。
    """
    for t in tools:
        if t.name == "fill_context_for_report":
            original_func = t.func

            @functools.wraps(original_func)
            def _wrapped(*args, _original=original_func, _holder=report_flag_holder, **kwargs):
                _holder["report"] = True
                logger.info("[middleware] 检测到 fill_context_for_report 调用，已设置 report=True")
                return _original(*args, **kwargs)

            t.func = _wrapped
            break
    return tools
