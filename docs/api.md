# API 接口文档

## Java 后端 (port 8080)

---

### POST /auth/register

用户注册。

- **URL**: `http://localhost:8080/auth/register`
- **Method**: `POST`
- **Headers**: `Content-Type: application/json`
- **Auth**: 无

**请求示例**:

```json
{
  "username": "lsh",
  "password": "666"
}
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": "注册成功"
}
```

---

### POST /auth/login

用户登录，返回 JWT token。

- **URL**: `http://localhost:8080/auth/login`
- **Method**: `POST`
- **Headers**: `Content-Type: application/json`
- **Auth**: 无

**请求示例**:

```json
{
  "username": "lsh",
  "password": "666"
}
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiaWF0IjoxNzE3NDk4MDAwfQ...",
    "userId": 1
  }
}
```

---

### POST /api/chat/ask

同步知识库智能问答 — 核心接口。

- **URL**: `http://localhost:8080/api/chat/ask`
- **Method**: `POST`
- **Headers**:
  - `Content-Type: application/json`
  - `Authorization: Bearer <jwt_token>`
- **Auth**: JWT (由 JwtInterceptor 校验)

**请求体**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| knowledgeBaseId | String | 是 | 知识库ID，对应当前 ChromaDB collection 名称 `agent` |
| sessionId | String | 是 | 会话ID，同一个 session 下的问题视为同一轮对话 |
| question | String | 是 | 用户问题，支持中文 |

**请求示例**:

```json
{
  "knowledgeBaseId": "agent",
  "sessionId": "test-session-001",
  "question": "扫地机器人怎么重置地图？"
}
```

**响应体**:

| 字段 | 类型 | 说明 |
|------|------|------|
| code | Integer | 状态码，200 表示成功 |
| message | String | 状态描述 |
| data.sessionId | String | 会话ID |
| data.answer | String | LLM 基于知识库生成的回答 |
| data.references | Array | 引用的知识库片段列表 |
| data.references[].source | String | 来源文档文件名 |
| data.references[].chunkId | String | 文档片段唯一标识 |
| data.references[].score | Double | 相似度分数 (0~1，越高越相关) |

**成功响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "sessionId": "test-session-001",
    "answer": "根据知识库资料，重置扫地机器人地图的步骤如下：\n1. 在APP首页点击设置图标\n2. 选择\"地图管理\"\n3. 点击\"重置地图\"按钮\n4. 确认操作后机器人将清除已有地图数据并重新建图。",
    "references": [
      {
        "source": "扫地机器人100问2.txt",
        "chunkId": "a1b2c3d4e5f6a7b8",
        "score": 0.6392
      },
      {
        "source": "维护保养.txt",
        "chunkId": "b2c3d4e5f6a7b8c9",
        "score": 0.6270
      }
    ]
  }
}
```

**知识库无匹配响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "sessionId": "test-session-001",
    "answer": "抱歉，当前知识库中没有找到与您问题相关的信息。",
    "references": []
  }
}
```

**RAG 服务不可用响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "sessionId": "test-session-001",
    "answer": "知识库检索服务暂时不可用，请稍后重试。",
    "references": []
  }
}
```

**限流响应示例**:

```json
{
  "code": 500,
  "message": "请求过于频繁，请稍后再试（每分钟最多 10 次）。"
}
```

**处理流程**:

```
JWT 校验 → Redis 限流检查 → RAG 检索 (Python) → 拼接 Prompt → LLM 调用 (DashScope) → 持久化 MySQL → 返回
```

---

### POST /api/chat/ask-async

异步知识库问答 — 投递 RabbitMQ 后立即返回，结果通过 WebSocket 推送。

- **URL**: `http://localhost:8080/api/chat/ask-async`
- **Method**: `POST`
- **Headers**:
  - `Content-Type: application/json`
  - `Authorization: Bearer <jwt_token>`
- **Auth**: JWT

**请求体** (与同步接口相同):

```json
{
  "knowledgeBaseId": "agent",
  "sessionId": "async-test-001",
  "question": "扫地机器人怎么重置地图？"
}
```

**响应示例**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "sessionId": "async-test-001",
    "status": "PENDING"
  }
}
```

> 启动要求：需要 RabbitMQ 运行，且 Java 以 `dev,mq` profile 启动（见 [startup.md](startup.md)）。
> WebSocket 推送格式见下方。

---

## WebSocket (port 8080)

### 连接地址

```
ws://localhost:8080/ws/chat?userId=1
```

> `userId` 需要与 JWT 登录返回的 userId 一致。

### 异步问答推送格式

Consumer 处理完成后通过 WebSocket 推送 JSON：

```json
{
  "type": "qa_result",
  "taskId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "sessionId": "async-test-001",
  "status": "SUCCESS",
  "answer": "根据知识库资料，重置扫地机器人地图的步骤如下...",
  "references": [
    {"source": "扫地机器人100问2.txt", "chunkId": "a1b2c3d4e5f6a7b8", "score": 0.6392}
  ],
  "errorMessage": null
}
```

失败时 status 为 `FAILED`，errorMessage 字段有值。

---

## Python RAG 服务 (port 8001, 仅 Java 后端调用)

> 以下接口不应由前端直接调用，仅供 Java 后端内部使用。

### GET /api/rag/health

健康检查。

- **URL**: `http://localhost:8001/api/rag/health`

**响应示例**:

```json
{
  "status": "ok",
  "collection": "agent (docs: 120)",
  "model": "qwen3-max"
}
```

---

### POST /api/rag/search

语义检索 — 从 ChromaDB 中检索 Top-K 相关文档片段。

- **URL**: `http://localhost:8001/api/rag/search`
- **Headers**: `Content-Type: application/json`

**请求体**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| knowledgeBaseId | String | 是 | 知识库ID (ChromaDB collection) |
| query | String | 是 | 用户查询 |
| topK | Integer | 否 | 返回数量，默认 5，范围 [1, 50] |

**请求示例**:

```json
{
  "knowledgeBaseId": "agent",
  "query": "扫地机器人怎么重置地图",
  "topK": 3
}
```

**响应示例**:

```json
{
  "chunks": [
    {
      "chunkId": "a1b2c3d4e5f6a7b8",
      "content": "在APP首页点击设置图标，选择地图管理，点击重置地图按钮...",
      "score": 0.6392,
      "source": "扫地机器人100问2.txt"
    },
    {
      "chunkId": "b2c3d4e5f6a7b8c9",
      "content": "定期重置地图可以提升机器人清扫效率...",
      "score": 0.6270,
      "source": "维护保养.txt"
    }
  ]
}
```

**score 说明**:

- 范围 `(0, 1]`，**越高越相关**
- `1.0` = 完全匹配，`≈0` = 完全不相关
- 服务端已将 ChromaDB 的 L2 distance 自动转换为相似度（`1/(1+distance)`）
- 结果按相关性降序排列

---

### POST /api/rag/index

文档入库 — 扫描 `data/` 目录，将新文件分块向量化后存入 ChromaDB。

- **URL**: `http://localhost:8001/api/rag/index`

**响应示例**:

```json
{
  "status": "indexed",
  "message": "Documents processed successfully. New files indexed, existing files skipped."
}
```

> 基于 MD5 去重，已入库的文件不会重复处理。
