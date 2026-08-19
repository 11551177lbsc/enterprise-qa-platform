from langgraph.prebuilt import create_react_agent
from model.factory import chat_model
from utils.prompt_loader import load_system_prompts, load_report_prompts
from agent.tools.agent_tools import (rag_summarize, get_weather, get_user_location, get_user_id,
                                     get_current_month, fetch_external_data, fill_context_for_report)
from agent.tools.middleware import agent_callback, wrap_tools_with_report_flag
from utils.logger_handler import logger


class ReactAgent:
    def __init__(self):
        # report 标记：当 fill_context_for_report 工具被调用时设置为 True
        self._report_flag = {"report": False}

        # 准备工具列表
        tools = [rag_summarize, get_weather, get_user_location, get_user_id,
                 get_current_month, fetch_external_data, fill_context_for_report]

        # 包装工具：检测 fill_context_for_report 调用并设置报告标记
        tools = wrap_tools_with_report_flag(tools, self._report_flag)

        # 使用 langgraph 的 create_react_agent 创建 Agent
        # prompt 参数为 callable 时，每次模型调用前都会执行，实现动态提示词切换
        self.agent = create_react_agent(
            model=chat_model,
            tools=tools,
            prompt=self._select_prompt,
        )

    def _select_prompt(self, state):
        """
        动态提示词选择：
        - 如果 fill_context_for_report 被调用过，使用报告生成提示词
        - 否则使用默认系统提示词
        """
        if self._report_flag.get("report", False):
            self._report_flag["report"] = False  # 重置标记
            logger.info("[prompt_switch] 切换到报告生成提示词")
            return load_report_prompts()
        return load_system_prompts()

    def execute_stream(self, query: str):
        input_dict = {
            "messages": [
                {"role": "user", "content": query},
            ]
        }

        # 使用 callbacks 传递监控回调
        for chunk in self.agent.stream(
            input_dict,
            stream_mode="values",
            config={"callbacks": [agent_callback]},
        ):
            latest_message = chunk["messages"][-1]
            if latest_message.content:
                yield latest_message.content.strip() + "\n"


if __name__ == '__main__':
    agent = ReactAgent()

    for chunk in agent.execute_stream("给我生成我的使用报告"):
        print(chunk, end="", flush=True)
