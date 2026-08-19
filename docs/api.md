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
- `POST /internal/agent-tools/tickets`
- `PATCH /internal/agent-tools/tickets/{id}`
- `POST /internal/agent-tools/notifications`

`X-Agent-Invocation-Id` 会写入 `agent_tool_execution` 主键；重复请求返回第一次的结果或安全停止，不会重复产生写操作。

## 原有 Java API

- `POST /auth/register`
- `POST /auth/login`
- `POST /api/chat/ask`
- `POST /api/chat/ask-async`
- `POST /chat/stream`
