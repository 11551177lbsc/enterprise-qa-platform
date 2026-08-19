# Agent 设计说明

## 为什么使用 Python + LangGraph

Agent 核心选择 Python，是为了直接使用成熟的图编排、checkpoint、interrupt、流式事件与评测生态；Java 不被替换，而是作为业务事实与权限的最终执行边界。这种拆分更贴近企业 AI 应用：模型编排可以快速迭代，账户、工单、事务和审计继续使用强类型业务服务。

## 状态与恢复

`AgentState` 只保存可恢复业务状态：run/thread/user、意图、待执行工具、工具结果、引用、审批和步数。JWT 属于短期敏感凭据，通过 `AgentContext` 在每次调用/恢复时传入，不保存到 Redis checkpoint。

开发测试使用 `InMemorySaver`；Redis 模式同时使用：

- LangGraph Redis Checkpointer：节点状态与 interrupt 恢复点。
- Redis Run Store：API 查询所需的运行元数据。
- Redis Stream：可重放的 SSE 事件。

## 工具分级

自动读取：知识库检索、当前用户、工单列表、工单详情。

必须审批：创建工单、修改工单、通知入队。

所有工具都在 Python 白名单内；Java 再次验证用户 JWT 和服务令牌。用户身份永远不来自模型参数。

## 幂等副作用

LangGraph 节点可能因网络或恢复而重试。每个工具调用在规划时生成稳定 `callId`，Java 将它作为 `agent_tool_execution.invocation_id`：

1. 首次执行先保留 `PROCESSING` 记录。
2. 业务操作完成后保存 `SUCCEEDED + response_json`。
3. 相同 ID 再次到达时返回历史结果，不重新写业务表。
4. 异常写入 `FAILED`，要求使用新的运行显式重试。

## 数据库版本

- V1：全新环境的用户、聊天和 RAG 历史基础表。
- V2：`support_ticket`、`notification_outbox`、`agent_tool_execution`。

对已有非空数据库启用 `baseline-on-migrate`，以版本 1 纳管后只执行 V2；迁移文件一旦执行就不得修改。

## 失败边界

- 规划器或模型不可用：`auto` 模式降级为确定性规则规划，测试不依赖外部模型。
- Java 工具超时：运行标记为 failed，不向模型伪造成功结果。
- 未批准/拒绝：写工具完全不调用。
- Redis 不可用：生产启动失败；不会静默退回不可恢复的内存模式。
- 外部通知：只写 Outbox，发送消费者未实现前不会产生真实邮件副作用。
