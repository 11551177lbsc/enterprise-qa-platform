# API 文档

## 用户侧 Agent API（Python，8001）

除健康检查和兼容 RAG 接口外，Agent API 都要求：

```http
Authorization: Bearer <Java 登录返回的 JWT>
```

### 创建运行

`POST /api/agent/runs`

```json
{
  "message": "创建工单：扫地机器人无法回充",
  "threadId": "可选的会话ID"
}
```

返回 HTTP 202：

```json
{
  "runId": "...",
  "threadId": "...",
  "status": "queued"
}
```

### 查询运行

`GET /api/agent/runs/{runId}`

状态：`queued`、`running`、`waiting_approval`、`completed`、`failed`、`cancelled`。

知识问答结束后会返回面向业务页面的结构化处理结论：

```json
{
  "status": "completed",
  "answer": "根据知识库资料……",
  "citations": [
    {
      "chunkId": "...",
      "source": "故障排除.txt",
      "score": 0.68,
      "excerpt": "……"
    }
  ],
  "resolution": {
    "outcome": "answered",
    "confidence": 0.68,
    "reason": "知识库已命中高于安全阈值的资料。",
    "nextSteps": ["按知识库步骤逐项排查"],
    "ticketDraft": null
  }
}
```

`outcome` 可为 `answered`、`needs_clarification`、`escalation_recommended`、`action_completed`。当知识证据低于 `RAG_CONFIDENCE_THRESHOLD` 时，接口返回 `escalation_recommended` 和 `ticketDraft`，但不会自动创建工单；用户仍需发起升级并批准写操作。

工单草稿还可包含 `category`、`productModel`、`knowledgeConfidence` 和 `escalationReason`。分类取值为 `DEVICE`、`ACCOUNT`、`ORDER`、`BILLING`、`SAFETY`、`OTHER`。

### 订阅事件

`GET /api/agent/runs/{runId}/events`

响应类型为 `text/event-stream`。事件包括 `run.queued`、`run.started`、`approval.required`、`approval.decided`、`run.completed`、`run.failed`、`run.cancelled`。

### 审批写工具

`POST /api/agent/runs/{runId}/approvals/{approvalId}`

```json
{
  "approved": true,
  "reason": "确认创建售后工单"
}
```

拒绝时将 `approved` 设为 `false`，工具不会执行。

### 取消运行

`POST /api/agent/runs/{runId}/cancel`

## RAG 兼容 API（Python，8001）

- `GET /api/rag/health`
- `POST /api/rag/search`
- `POST /api/rag/index`：默认 403；设置 `RAG_ALLOW_INDEXING=true` 后仍需提供 `X-Agent-Service-Token`。

检索请求：

```json
{
  "knowledgeBaseId": "agent",
  "query": "机器吸力下降怎么办",
  "topK": 5
}
```

## 内部工具网关（Java，8080）

以下接口不提供给浏览器直接调用，同时要求用户 JWT、`X-Agent-Service-Token` 和 `X-Agent-Invocation-Id`：

- `GET /internal/agent-tools/users/me`
- `GET /internal/agent-tools/tickets`
- `GET /internal/agent-tools/tickets/{id}`
- `GET /internal/agent-tools/tickets/{id}/timeline`
- `POST /internal/agent-tools/tickets`
- `PATCH /internal/agent-tools/tickets/{id}`
- `POST /internal/agent-tools/notifications`

`X-Agent-Invocation-Id` 会写入 `agent_tool_execution` 主键；重复请求返回第一次的结果或安全停止，不会重复产生写操作。

创建工单时 Java 会按优先级计算 SLA：`URGENT=4小时`、`HIGH=24小时`、`MEDIUM=72小时`、`LOW=120小时`，并写入第一条工单时间线事件。

## 支持运营 API（Java，8080）

这些接口要求用户 JWT，但不要求 Agent 服务令牌：

- `GET /api/support/me`：返回当前支持中心角色；无角色记录时为 `USER`。
- `GET /api/support/knowledge-gaps`：支持人员、知识管理员和管理员查看未关闭知识缺口。
- `PATCH /api/support/knowledge-gaps/{id}`：更新为 `OPEN`、`IN_REVIEW` 或 `RESOLVED`。

答案反馈必须提交给 `POST /api/agent/runs/{runId}/feedback`。Python 会先验证该运行属于当前用户且已经完成，再通过带服务令牌的 Java 内部接口落库；浏览器不能直接伪造其他运行的知识缺口。

```json
{
  "runId": "Agent运行ID",
  "question": "机器人无法回充怎么办",
  "outcome": "UNRESOLVED",
  "comment": "执行排查步骤后仍然失败",
  "confidence": 0.68
}
```

## 原有 Java API

- `POST /auth/register`
- `POST /auth/login`
- `POST /api/chat/ask`
- `POST /api/chat/ask-async`
- `POST /chat/stream`
