# 面试准备文档：企业知识库智能问答平台

> 适用于：Java 后端实习面试
> 项目地址：`e:\enterprise-qa-platform`
> 当前稳定版本：同步 RAG 问答（`POST /api/chat/ask`）

---

## 一、项目一句话介绍

这是一个 **企业知识库智能问答平台**，员工上传 PDF/TXT 文档到知识库后，可以在聊天框里直接用自然语言提问，系统从文档中检索相关内容，用大模型生成有信息来源的答案。

**为什么要 Java + Python 拆分？** Python 负责 RAG 向量检索（LangChain + ChromaDB + Embedding 模型），Java Spring Boot 负责用户认证、限流、编排调度、持久化和异常兜底。两个组件各司其职，通过 HTTP REST 解耦——Python 不需要感知用户、权限和业务逻辑，Java 不需要处理向量化和文档解析。

---

## 二、核心调用链（`POST /api/chat/ask` 同步问答）

每步标注：**序号 → 所属文件 → 类名 → 方法名 → 做了什么**

```
1. 浏览器 POST /api/chat/ask
   Header: Authorization: Bearer <JWT>
   Body: { knowledgeBaseId, sessionId, question }

2. KnowledgeQaController.ask()                    [controller/KnowledgeQaController.java:33]
   ├── extractUserId(token) 从 JWT 解析 userId      [第78行]
   └── 调用 knowledgeQaService.ask(userId, kbId, sessionId, question)

3. KnowledgeQaServiceImpl.ask()                   [service/impl/KnowledgeQaServiceImpl.java:153]
   ├── checkRateLimit(userId)                     [第227行]
   │      Redis key: qa:rate:user:{userId}:{minuteBucket}
   │      INCR + 首次设置 1 分钟过期
   │      阈值: qa.rate-limit.max-per-minute (默认 10)
   ├── 超限 → 直接返回 "请求过于频繁"
   └── 通过 → executeQaCore(question, knowledgeBaseId)

4. KnowledgeQaServiceImpl.executeQaCore()          [第81行]
   │
   ├── Step 1: RAG 检索 ──────────────────────────────────────
   │   ragSearchClient.search(kbId, question, topK=3)         [client/RagSearchClient.java:33]
   │   │
   │   │   HTTP POST http://localhost:8001/api/rag/search
   │   │   Body: { knowledgeBaseId:"agent", query:"xxx", topK:3 }
   │   │
   │   │   ┌─ Python: rag_server.py  search()                  [rag_server.py:101]
   │   │   │   vector_store_service.vector_store.similarity_search_with_score(query, k)
   │   │   │   │                                               [rag/vector_store.py:14]
   │   │   │   │   Chroma(collection_name="agent",
   │   │   │   │         embedding_function=DashScopeEmbeddings("text-embedding-v4"),
   │   │   │   │         persist_directory="chroma_db")
   │   │   │   │   .similarity_search_with_score(query, k=3)
   │   │   │   │
   │   │   │   │   内部: query → 向量化(embedding) → ChromaDB L2距离检索 → 返回 top-3
   │   │   │   │
   │   │   │   L2 distance → similarity = 1/(1+distance)        [第68-75行]
   │   │   │   返回 SearchResponse { chunks: [...] }
   │   │   └─ 返回 JSON
   │   │
   │   └─ 反序列化为 RagSearchResponse { chunks, error }
   │
   ├── Step 2a: RAG 不可用（error != null）
   │     → answer = "知识库检索服务暂时不可用"  status = FAILED    [第91-96行]
   │
   ├── Step 2b: 检索为空（chunks == null || empty）
   │     → answer = "抱歉，当前知识库中没有找到与您问题相关的信息"   [第99-104行]
   │       status = NO_CONTEXT （不调 LLM，避免幻觉）
   │
   ├── Step 3: 构建 ReferenceDTO 列表                           [第109-116行]
   │     chunk.source + chunkId + score → references
   │
   ├── Step 4: 拼接 Prompt 上下文                                [第118-123行]
   │     "【参考资料1】(来源: xxx.pdf): 内容...\n"
   │     "【参考资料2】(来源: yyy.pdf): 内容..."
   │
   └── Step 5: 调用 LLM                                          [第128行]
        aiClient.chatWithSystem(SYSTEM_PROMPT, userMessage)      [ai/AiClient.java:85]
        │
        │   HTTP POST {llm.base-url}/chat/completions
        │   Header: Authorization: Bearer {llm.api-key}
        │   Body: { model:"qwen-turbo", messages: [
        │            { role:"system", content:"你是企业知识库助手..." },
        │            { role:"user", content:"参考资料：...\n用户问题：..." }
        │          ]}
        │
        │   阿里云 DashScope → 返回 LLM 生成的答案
        │
        └─ 解析 choices[0].message.content → 返回 answer
            失败 → answer = "AI 服务返回异常" status = FAILED    [第129-143行]

5. KnowledgeQaServiceImpl.ask() 收尾                           [第168行]
   ├── saveHistory(userId, sessionId, question, answer,         [第243行]
   │               references, status, errorMessage)
   │     INSERT INTO qa_history(...) → MySQL
   │
   └── 返回 KnowledgeQaResponse { sessionId, answer, references } [第171-175行]

6. KnowledgeQaController.ask()                                  [第44行]
   return ResultUtil.success(response)

7. 浏览器收到 JSON:
   { code:200, message:"success",
     data: { sessionId, answer, references:[{source, chunkId, score}] } }
```

### 调用链速记

```
前端 → Controller → Service(限流) → RagSearchClient(HTTP) → Python FastAPI → ChromaDB
                                              ↓
                               Service(拼prompt) → AiClient(HTTP) → 阿里云 LLM
                                              ↓
                               Service(存qa_history) → MySQL → 返回结果
```

---

## 三、关键文件说明

### 3.1 必须掌握（面试核心文件，要知道细节）

| 文件 | 路径 | 为什么重要 |
|------|------|-----------|
| **KnowledgeQaServiceImpl** | `aiLLL/src/main/java/com/haust/ailll/service/impl/KnowledgeQaServiceImpl.java` | **主流程编排**：限流→RAG→拼Prompt→LLM→持久化，面试所有问题都绕不开它 |
| **KnowledgeQaController** | `aiLLL/src/main/java/com/haust/ailll/controller/KnowledgeQaController.java` | **对外API入口**：`POST /api/chat/ask` 和 `/ask-async`，JWT 解析 |
| **RagSearchClient** | `aiLLL/src/main/java/com/haust/ailll/client/RagSearchClient.java` | **Java调Python的桥**：RestTemplate POST 到 `localhost:8001/api/rag/search` |
| **AiClient** | `aiLLL/src/main/java/com/haust/ailll/ai/AiClient.java` | **LLM调用**：chatWithSystem() 方法，发 system+user messages 到阿里云 |
| **JwtInterceptor** | `aiLLL/src/main/java/com/haust/ailll/interceptor/JwtInterceptor.java` | **认证拦截器**：从 Header 取 JWT → 解析 userId → Redis 校验 token |
| **JwtUtil** | `aiLLL/src/main/java/com/haust/ailll/util/JwtUtil.java` | **JWT工具**：生成和解析（HS256签名，24小时过期） |
| **rag_server.py** | `ragagent/rag_server.py` | **Python RAG API**：FastAPI 端点 `POST /api/rag/search` |
| **vector_store.py** | `ragagent/rag/vector_store.py` | **向量存储服务**：ChromaDB 初始化、文档加载(MD5去重)、相似度检索 |
| **application.yml** | `aiLLL/src/main/resources/application.yml` | **总配置**：端口、Redis、MySQL、RabbitMQ、LLM参数、RAG地址、限流参数 |

### 3.2 了解即可（支持性文件，知道作用就行）

| 文件 | 路径 | 作用 |
|------|------|------|
| **QaHistory** | `aiLLL/.../entity/QaHistory.java` | qa_history 实体 + 状态常量（SUCCESS/FAILED/NO_CONTEXT/PENDING） |
| **QaHistoryMapper** | `aiLLL/.../mapper/QaHistoryMapper.java` | MyBatis CRUD 接口 |
| **QaHistoryMapper.xml** | `aiLLL/src/main/resources/mapper/QaHistoryMapper.xml` | SQL 映射（insert/findById/updateStatus/findBySessionId/findByUserId） |
| **RagConfig** | `aiLLL/.../config/RagConfig.java` | 绑定 `rag.base-url`（指向 Python 服务地址） |
| **LlmConfig** | `aiLLL/.../config/LlmConfig.java` | 绑定 `llm.*`（apiKey/baseUrl/model） |
| **AiConfig** | `aiLLL/.../config/AiConfig.java` | 旧 LLM 配置（`ai.*`），用于 ChatController |
| **RedisUtil** | `aiLLL/.../util/RedisUtil.java` | Redis 封装（set/get/delete/incr），基于 StringRedisTemplate |
| **KnowledgeQaRequest** | `aiLLL/.../dto/KnowledgeQaRequest.java` | 请求 DTO：knowledgeBaseId, sessionId, question |
| **KnowledgeQaResponse** | `aiLLL/.../dto/KnowledgeQaResponse.java` | 响应 DTO：sessionId, answer, references |
| **RagSearchRequest** | `aiLLL/.../dto/RagSearchRequest.java` | 发给 Python 的请求 DTO |
| **RagSearchResponse** | `aiLLL/.../dto/RagSearchResponse.java` | Python 返回的响应 DTO |
| **RagChunkDTO** | `aiLLL/.../dto/RagChunkDTO.java` | 单个文档片段：chunkId, content, score, source |
| **ReferenceDTO** | `aiLLL/.../dto/ReferenceDTO.java` | 引用来源：source, chunkId, score |
| **model/factory.py** | `ragagent/model/factory.py` | Python 模型工厂：ChatTongyi(qwen3-max)、DashScopeEmbeddings(text-embedding-v4) |
| **config/chroma.yml** | `ragagent/config/chroma.yml` | ChromaDB 配置：collection名、chunk_size=200、persist目录 |
| **config/rag.yml** | `ragagent/config/rag.yml` | 模型名称：chat_model_name, embedding_model_name |

### 3.3 暂时不用讲

| 文件 | 原因 |
|------|------|
| `ChatController.java` | 旧 AI Chat 流式对话，非当前 RAG 主链路 |
| `AuthController.java` / `UserController.java` | 注册登录，非问答核心 |
| `RateLimitInterceptor.java` / `RateLimitService.java` | 旧全局限流拦截器，RAG 流程已改用 Service 层限流 |
| `QaTaskProducer.java` / `QaTaskConsumer.java` | 异步 MQ 方案，当前稳定版本不使用 |
| `QaWebSocketPushService.java` / `WebSocketSessionManager.java` | WebSocket 推送，属于后续扩展 |
| `RabbitMQConfig.java` | MQ 队列声明（含 `qa.rag.task.queue`），暂未激活 |
| `UserMapper.xml` / `ChatMapper.xml` | 旧功能 Mapper |
| `agent/react_agent.py` / `agent/tools/` | Streamlit 独立 Agent 应用，非 Java 调用路径 |
| `app.py` | Streamlit UI，独立于后端服务 |
| `rag/rag_service.py` | LangChain 摘要链（仅 Streamlit 使用），Java 路径不用 |

---

## 四、技术亮点（附代码证据）

### 4.1 Spring Boot 分层设计

**文件**：`KnowledgeQaController.java` → `KnowledgeQaServiceImpl.java` → `RagSearchClient.java` → `AiClient.java`

**亮点**：
- **Controller** 只做路由和参数提取，不写业务逻辑
- **Service** 做编排（限流→RAG→LLM→持久化），是核心调度层
- **Client** 封装外部 HTTP 调用（Python RAG、LLM API），隐藏底层细节
- 每层职责单一，方便单测和替换实现

**代码证据**：
```
Controller.ask() → 解析 JWT → Service.ask()
Service.ask()   → 限流 → executeQaCore() → Client.search() + AiClient.chatWithSystem() → 持久化
Client.search() → RestTemplate POST → 返回 DTO
AiClient.chatWithSystem() → RestTemplate POST → 返回 String
```

### 4.2 JWT 登录鉴权

**文件**：[`JwtUtil.java`](aiLLL/src/main/java/com/haust/ailll/util/JwtUtil.java) + [`JwtInterceptor.java`](aiLLL/src/main/java/com/haust/ailll/interceptor/JwtInterceptor.java)

**亮点**：
- 使用 jjwt 库（HMAC-SHA256 签名），生成时写入 `userId` 作为 subject，24小时过期
- `JwtInterceptor.preHandle()` 拦截所有请求（`/**`），从 `Authorization` Header 取 token
- 校验分两步：① 解析 JWT 获取 userId ② 查 Redis 确认 token 未被删除（支持服务端主动踢出）
- OPTIONS 预检请求放行

**代码证据**：
```java
// JwtUtil.parseToken() — 解析并返回 userId
Claims claims = Jwts.parserBuilder().setSigningKey(getKey()).build()
        .parseClaimsJws(token).getBody();
return Long.valueOf(claims.getSubject());

// JwtInterceptor.preHandle() — 校验流程
Long userId = jwtUtil.parseToken(token);
String redisToken = redisUtil.get("login:" + userId);
if (redisToken == null) → 返回 401 "登录过期"
```

### 4.3 Redis token 存储

**文件**：[`RedisUtil.java`](aiLLL/src/main/java/com/haust/ailll/util/RedisUtil.java) + [`JwtInterceptor.java:44`](aiLLL/src/main/java/com/haust/ailll/interceptor/JwtInterceptor.java#L44-L55)

**亮点**：
- 用户登录后 token 存 Redis，key = `login:{userId}`
- 拦截器校验时先解析 JWT 拿到 userId，再查 Redis 确认 token 存在
- 删除 Redis key = 服务端强制登出（踢人能力）

**代码证据**：
```java
// JwtInterceptor 第44行
String redisKey = "login:" + userId;
String redisToken = redisUtil.get(redisKey);
if (redisToken == null) {
    response.setStatus(401);
    response.getWriter().write("登录过期");
    return false;
}
```

### 4.4 Redis 用户级限流

**文件**：[`KnowledgeQaServiceImpl.java`](aiLLL/src/main/java/com/haust/ailll/service/impl/KnowledgeQaServiceImpl.java) 第 227-241 行 `checkRateLimit()`

**亮点**：
- 使用 **滑动分钟窗口**：key = `qa:rate:user:{userId}:{minuteBucket}`，`minuteBucket = System.currentTimeMillis() / 60000`
- Redis `INCR` 原子自增，首次设 1 分钟过期
- 阈值通过 `@Value("${qa.rate-limit.max-per-minute:10}")` 注入，可配置
- **Redis 挂了不影响主流程**：catch 异常时 `return true`（放行），不阻塞业务
- 限流粒度：按 userId 隔离；解析不到 userId 时降级为 `qa:rate:anonymous`

**代码证据**：
```java
private boolean checkRateLimit(Long userId) {
    if (redisUtil == null) return true;
    try {
        String rateKey = (userId != null) ? "qa:rate:user:" + userId : "qa:rate:anonymous";
        long bucket = System.currentTimeMillis() / 60000;
        String key = rateKey + ":" + bucket;
        Long count = redisUtil.incr(key);
        if (count != null && count == 1L) {
            redisUtil.set(key, "1", 1);  // 首次设1分钟过期
        }
        return count == null || count <= maxPerMinute;
    } catch (Exception e) {
        return true;  // Redis 不可达 → 放行
    }
}
```

### 4.5 MySQL qa_history 状态追踪

**文件**：[`QaHistory.java`](aiLLL/src/main/java/com/haust/ailll/entity/QaHistory.java) + [`QaHistoryMapper.xml`](aiLLL/src/main/resources/mapper/QaHistoryMapper.xml)

**亮点**：
- 每次问答都写入 `qa_history` 表，记录完整的问答生命周期
- 四种状态枚举：`SUCCESS`（正常完成）、`NO_CONTEXT`（检索为空未调LLM）、`FAILED`（RAG或LLM异常）、`PENDING`（已投递MQ待处理）
- `references_json` 存 JSON 数组，保留引用的来源、片段ID和相似度分数
- `error_message` 记录失败原因，方便排查
- 支持按 `session_id` 查询一轮对话的所有问答、按 `user_id` 查询用户历史（最多100条）

**表结构**：
```sql
qa_history:
  id (自增主键)
  user_id (从JWT解析)
  session_id (会话标识)
  question (用户问题)
  answer (LLM回答)
  references_json (引用来源JSON)
  status (SUCCESS/NO_CONTEXT/FAILED/PENDING)
  error_message (失败原因)
  create_time / update_time
```

### 4.6 Java 调 Python RAG 服务

**文件**：[`RagSearchClient.java`](aiLLL/src/main/java/com/haust/ailll/client/RagSearchClient.java) + [`RagConfig.java`](aiLLL/src/main/java/com/haust/ailll/config/RagConfig.java) + [`rag_server.py`](ragagent/rag_server.py)

**亮点**：
- Java 通过 `RestTemplate.postForObject()` 发 HTTP POST 到 Python FastAPI
- Python 服务地址通过 `@ConfigurationProperties(prefix = "rag")` 从配置文件注入，`application.yml` 中配 `rag.base-url: http://localhost:8001`
- Python 端 FastAPI 自动做请求体校验（Pydantic SearchRequest），自动序列化响应
- Java 端捕获所有异常（连接超时、拒绝、5xx），返回带 error 字段的响应而非抛异常

**代码证据**：
```java
// RagSearchClient.search() — Java 发请求
String url = ragConfig.getBaseUrl() + "/api/rag/search";
return restTemplate.postForObject(url, request, RagSearchResponse.class);
// 异常捕获 → 返回 RagSearchResponse{error: "RAG service unavailable: ..."}
```
```python
# rag_server.py — Python 接收并处理
@app.post("/api/rag/search")
async def search(request: SearchRequest):
    results = vector_store_service.vector_store.similarity_search_with_score(
        request.query, k=request.topK)
    # ... L2→similarity 转换, 排序, 返回
    return SearchResponse(chunks=chunks)
```

### 4.7 Prompt 构造与 LLM API 调用

**文件**：[`KnowledgeQaServiceImpl.java`](aiLLL/src/main/java/com/haust/ailll/service/impl/KnowledgeQaServiceImpl.java) 第 118-128 行 + [`AiClient.chatWithSystem()`](aiLLL/src/main/java/com/haust/ailll/ai/AiClient.java) 第 85 行

**亮点**：
- System Prompt 明确定义角色："仅基于参考资料回答"，"没有就说不知道"——这是 RAG 防幻觉的关键设计
- 参考资料的编号格式 `【参考资料1】(来源: xxx.pdf)` 让 LLM 能引用来源
- LLM 调用使用 OpenAI 兼容接口（`/chat/completions`），方便切换模型供应商
- 配置通过 `@ConfigurationProperties(prefix = "llm")` 注入，支持环境变量覆盖（`${LLM_API_KEY:...}`）

**代码证据**：
```java
// System Prompt（第52-57行）
private static final String SYSTEM_PROMPT =
    "你是企业知识库智能问答助手。请仅基于以下提供的参考资料回答用户问题。" +
    "如果参考资料中没有相关信息，请如实告知用户：" +
    "\"抱歉，当前知识库中没有找到与您问题相关的信息。\"\n" +
    "回答时请引用参考资料的编号（如【参考资料1】），以便用户追溯信息来源。";

// Prompt 拼接（第120-123行）
ctx.append(String.format("【参考资料%d】(来源: %s): %s\n",
    i + 1, c.getSource(), c.getContent()));
String userMessage = String.format("参考资料：\n%s\n用户问题：%s", ctx.toString(), question);

// AiClient 调用 LLM（第85行）
String answer = aiClient.chatWithSystem(SYSTEM_PROMPT, userMessage);
```

### 4.8 异常兜底处理

**文件**：[`KnowledgeQaServiceImpl.java`](aiLLL/src/main/java/com/haust/ailll/service/impl/KnowledgeQaServiceImpl.java) `executeQaCore()` 方法 (第 81-146 行)

**三层兜底**：

| 异常场景 | 处理方式 | status | 用户看到的内容 |
|---------|---------|--------|-------------|
| Python RAG 服务不可达 | RagSearchClient catch 异常返回 error，Service 检查后直接返回 | FAILED | "知识库检索服务暂时不可用，请稍后重试" |
| RAG 检索无结果 | chunks 为空，不调 LLM，直接返回 | NO_CONTEXT | "抱歉，当前知识库中没有找到与您问题相关的信息" |
| LLM 返回空/解析失败 | catch 异常，返回兜底文案 | FAILED | "AI 服务返回异常，请稍后重试" |

**代码证据**：
```java
// 场景1: RAG 服务不可达 (第91-96行)
if (ragResponse.getError() != null && !ragResponse.getError().isEmpty()) {
    result.answer = "知识库检索服务暂时不可用，请稍后重试。";
    result.status = QaHistory.STATUS_FAILED;
    result.errorMessage = ragResponse.getError();
    return result;
}

// 场景2: 检索为空 (第99-104行)
if (chunks == null || chunks.isEmpty()) {
    result.answer = "抱歉，当前知识库中没有找到与您问题相关的信息。";
    result.status = QaHistory.STATUS_NO_CONTEXT;
    return result;  // 不调 LLM
}

// 场景3: LLM 异常 (第138-143行)
catch (Exception e) {
    result.answer = "AI 服务调用失败，请稍后重试。";
    result.status = QaHistory.STATUS_FAILED;
    result.errorMessage = "LLM call exception: " + e.getMessage();
}
```

---

## 五、面试高频问题与回答（28 题）

### 第1部分：项目整体（1-5 题）

**Q1：简单介绍一下这个项目**

> 这是一个企业知识库智能问答平台。员工上传公司的 PDF、TXT 文档后，系统会把文档切分、向量化存入 ChromaDB。用户在聊天框里提问题，系统先从知识库里检索最相关的文档片段（RAG），再把检索结果拼成 Prompt 发给大模型生成有来源依据的答案。
>
> 我负责的是 Java 后端部分，用 Spring Boot 做主体框架，包括 JWT 登录认证、Redis 限流、调用 Python RAG 服务、拼接 Prompt 调 LLM、MySQL 持久化问答记录。

**Q2：为什么选择 Java + Python 服务化拆分？**

> 主要是**职责分离**。Python 生态在 RAG 方面成熟——LangChain 处理文档加载和切分，ChromaDB 做向量存储，DashScopeEmbeddings 做向量化，用 FastAPI 包一层 HTTP 接口就可以复用。
>
> Java 做 Web 工程更有优势——Spring Boot 的拦截器、配置管理、MyBatis、Redis 集成都很成熟。用户认证、限流、编排调度、持久化这些业务逻辑放在 Java 层。
>
> 拆开后，Python 完全不需要感知用户、权限、SQL，Java 也不需要处理文档解析和向量化。两边通过一个 REST 接口解耦，后续想换向量数据库或换 LLM 供应商，都不影响另一边。

**Q3：这个项目和普通 AI Chat 项目有什么区别？**

> 核心区别是有**知识库约束**。普通 AI Chat 是用户问什么模型直接答什么，模型可能编造信息（幻觉）。我们这个项目加了 RAG 环节：
>
> 1. 用户问题 → 先去知识库检索相关文档
> 2. 检索结果作为"参考资料"拼到 Prompt 里
> 3. System Prompt 约束模型"仅基于参考资料回答，没有就说不知道"
>
> 这样答案可以追溯到原始文档，避免 AI 胡编公司政策。另外我们的 qa_history 表记录了完整的问答状态（SUCCESS / NO_CONTEXT / FAILED），不只是存聊天内容。

**Q4：为什么前端只访问 Java，不直接调 Python？**

> 三个原因：
> 1. **安全**：Python RAG 服务没有用户认证，直接暴露内网端口很危险
> 2. **职责**：Java 负责编排（限流→RAG→LLM→持久化），前端不需要感知底层有几个服务
> 3. **扩展性**：后续换向量数据库或加缓存，前端代码不受影响

**Q5：Python 为什么只做 RAG 检索，不做摘要？**

> Java 调用路径下，Python 的 `POST /api/rag/search` 只返回原始文档片段（chunks），不做摘要。原因：
> 1. Java 要给 LLM 传完整的原始上下文，让 LLM 自己判断哪些信息有用
> 2. Prompt 构造、引用编号标注、LLM 调用这些编排逻辑统一在 Java 侧维护
> 3. Python 端的 RagSummarizeService（LangChain 摘要链）是给 Streamlit Agent UI 用的，属于不同的应用场景

---

### 第2部分：认证与安全（6-8 题）

**Q6：JWT 登录鉴权是怎么实现的？**

> `JwtInterceptor` 拦截所有请求（`/**`），从 Authorization Header 取 token，去掉 "Bearer " 前缀后用 jjwt 库解析出 userId。然后去 Redis 查 `login:{userId}` 这个 key 是否存在——存在说明 token 有效，不存在说明已过期或被服务端踢出。返回 401 的三种情况：token 为空、解析失败、Redis 中不存在。

**Q7：JWT token 怎么生成和存储？**

> `JwtUtil.generateToken(userId)` 用 HMAC-SHA256 签名，subject 存 userId，24 小时过期。登录成功后把 token 存 Redis（key = `login:{userId}`）。校验时既要能解析 JWT，也要 Redis key 存在——这样服务端可以主动删除 key 实现强制登出。

**Q8：如果有人伪造 JWT 怎么办？**

> jjwt 库在解析时会校验 HMAC 签名，签名不匹配会抛异常，`JwtInterceptor` catch 后返回 401 "token无效"。密钥是服务端内存中的字符串常量，除非泄露否则无法伪造。

---

### 第3部分：限流与高可用（9-12 题）

**Q9：Redis 限流具体怎么做？**

> 在 `KnowledgeQaServiceImpl.checkRateLimit()` 中做用户级分钟限流。Key 设计为 `qa:rate:user:{userId}:{minuteBucket}`，其中 `minuteBucket = 当前毫秒时间戳 / 60000`，每分钟一个桶。用 Redis INCR 原子自增计数，首次 `count == 1` 时设置 1 分钟过期。阈值从配置文件注入，默认 10 次/分钟。如果 Redis 挂了，catch 异常直接 return true 放行——不阻塞正常业务。

**Q10：为什么在 Service 层做限流而不是在拦截器里？**

> 当前稳定版本的限流是专门针对 RAG 问答的，限流粒度和业务逻辑相关（需要 userId 做 key、需要可配置阈值）。旧的 `RateLimitInterceptor` 是一个通用拦截器，但在 RAG 流程中我们用 Service 层限流更灵活——可以直接控制返回的文案内容、可以与其他 RAG 错误处理统一风格的兜底逻辑。

**Q11：Python RAG 服务不可用时怎么办？**

> `RagSearchClient.search()` 中 `RestTemplate.postForObject()` 如果抛异常（连接超时、拒绝、5xx），会 catch 住，返回一个带 error 字段的 `RagSearchResponse`。上层的 `executeQaCore()` 检查到 error 不为空，status 设为 FAILED，返回用户 "知识库检索服务暂时不可用，请稍后重试"，同时记录到 qa_history 的 error_message 字段。Java 服务本身不会崩。

**Q12：LLM 调用失败怎么处理？**

> `AiClient.chatWithSystem()` 中：如果 HTTP 返回正常但解析失败（JSON 结构不对），返回字符串 "AI解析失败"；如果网络异常，在 `executeQaCore()` 中 catch 后设 status = FAILED，返回 "AI 服务调用失败，请稍后重试"。两种情况都会持久化到 qa_history。

---

### 第4部分：数据存储（13-15 题）

**Q13：qa_history 表设计思路是什么？**

> 核心字段：user_id（谁问的）、session_id（哪一轮对话）、question（问什么）、answer（答什么）、references_json（引用了哪些文档片段）、status（处理结果：SUCCESS/NO_CONTEXT/FAILED/PENDING）、error_message（失败原因）。
>
> 设计目的是追踪每次问答的完整生命周期，不是只记录成功的情况。NO_CONTEXT 状态能让我们知道哪些问题知识库覆盖不到，FAILED 状态能快速定位是 RAG 还是 LLM 的故障。

**Q14：references_json 存什么？为什么用 JSON 字段？**

> 存一个数组 `[{source, chunkId, score}]`，记录这次回答引用了哪些文档片段。用 JSON 字段而不是外键关联，因为引用来源结构简单、不需要单独建表做复杂查询，JSON 可以直接反序列化给前端展示。

**Q15：RAG 检索为空时为什么状态是 NO_CONTEXT 而不是 FAILED？**

> 因为检索为空**不是系统故障**，是知识库里确实没有相关内容。区分 NO_CONTEXT 和 FAILED 有助于后续运营分析——比如统计哪些问题高频出现但找不到答案，说明应该补充相关文档。

---

### 第5部分：架构与扩展（16-22 题）

**Q16：当前稳定版本为什么不用 RabbitMQ 异步？**

> 同步接口 `POST /api/chat/ask` 已经覆盖了核心 RAG 问答流程，且 RAG 检索 + LLM 调用的延迟在可接受范围内。同步模式调试方便，排查问题快。MQ 异步更适合高并发排队或需要离线处理的场景，当前阶段不是必需。但代码已备好——`qa.rag.task.queue` 队列、`QaTaskProducer`、`QaTaskConsumer`、`QaWebSocketPushService` 都已实现，加 Profile 即可激活。

**Q17：RabbitMQ + WebSocket 后续怎么扩展？**

> 当前代码已就绪：
> - `RabbitMQConfig` 声明了独立队列 `qa.rag.task.queue`（不复用旧 AI Chat 队列）
> - `QaTaskProducer.sendTask()` 把任务消息（taskId, historyId, userId, question 等）序列化为 JSON 投递
> - `QaTaskConsumer.handleRagTask()` 监听队列，调用与同步接口相同的 `executeQaCore()`，更新 qa_history 状态，通过 `QaWebSocketPushService` 推送结果
> - `WebSocketSessionManager` 用 `ConcurrentHashMap<userId, WebSocketSession>` 管理连接
>
> 扩展时只需：启动 RabbitMQ → 激活 `application-mq.yml` Profile → 前端建立 WebSocket 连接等待推送。

**Q18：如果高并发场景怎么优化？**

> 1. **RAG 层面**：Python FastAPI 是异步的（`async def`），可以并发处理多个检索请求；如果需要进一步扩展可以用多 worker（gunicorn）
> 2. **缓存**：对相同或相似问题做 Redis 缓存（问题 hash → 答案 + references），避免重复的 RAG+LLM 调用
> 3. **MQ 异步化**：切到异步接口 `POST /api/chat/ask-async`，请求入队后立即返回 taskId，后台消费者批量处理
> 4. **LLM 层面**：可以引入结果缓存，相同 Prompt 不重复调 API
> 5. **数据库**：qa_history 按时间分表，查询加索引

**Q19：如果有多知识库怎么设计？**

> 当前已经支持——`RagSearchRequest` 里有 `knowledgeBaseId` 字段，对应 ChromaDB 的 collection 名。每个知识库一个独立的 ChromaDB collection，切换知识库就是换 collection 名。Java 端只需要在请求里传不同的 knowledgeBaseId，Python 端无需改动。前端加上知识库选择下拉框即可。

**Q20：如果要支持多轮对话怎么做？**

> 当前已通过 `sessionId` 将同一轮对话的问答关联起来（qa_history 支持按 session_id 查询）。要做真正的多轮对话，需要：
> 1. 在 `executeQaCore()` 中查该 session 的历史消息（近几轮），拼到 messages 数组里
> 2. AiClient 支持传 messages 数组而不是单轮 system+user
> 3. 多轮场景下 RAG 检索需要融合对话上下文，可能需要改写 query

**Q21：如果要切换 LLM 供应商怎么做？**

> 当前配置已支持——LLM 地址、API Key、模型名都通过 `application.yml` 的 `llm.*` 配置，支持环境变量覆盖（`${LLM_API_KEY:default}`）。`AiClient.chatWithSystem()` 使用 OpenAI 兼容的 `/chat/completions` 接口格式（system + user messages），只要新供应商兼容这套 API，改配置就行，不需要改代码。

**Q22：如果要换向量数据库（比如从 ChromaDB 换 Milvus）怎么做？**

> 只需要改 Python 端 `vector_store.py` 中 `VectorStoreService.__init__()` 的初始化代码，把 `Chroma(...)` 换成 Milvus 的客户端，`similarity_search_with_score()` 的调用签名保持不变即可。Java 端完全不感知——因为 Java 只调 `/api/rag/search` HTTP 接口，不关心底层是什么数据库。

---

### 第6部分：项目经验与总结（23-28 题）

**Q23：这个项目中你最大的收获是什么？**

> 最大的收获是理解了**后端作为编排层**的定位。Java 不只是简单把请求转发给 Python 再把 Python 的结果返回给前端——中间做了用户认证、限流、异常兜底、Prompt 构造、状态持久化这些事情。每层兜底都有对应的错误信息和状态码，这是企业级应用和 demo 的最大区别。

**Q24：你在项目中遇到的问题和怎么解决的？**

> 最有代表性的是 ChromaDB 返回的 **L2 距离**问题。`similarity_search_with_score()` 返回的是欧氏距离（越小越相似），但前端展示需要"相似度分数"（越大越相似）。我在 `rag_server.py` 中用 `similarity = 1 / (1 + distance)` 转换，保证了排序语义正确。另外 `RagSearchClient` 最初异常处理是直接 throw 导致 500，后来改成返回带 error 字段的响应，让上层优雅降级。

**Q25：项目中有没有印象深刻的技术细节？**

> 1. **限定流的设计**：用 `currentTimeMillis / 60000` 做分钟桶 + Redis INCR 原子自增，简单有效。关键是加了 Redis 不可达时的 fallback——不阻塞业务。
> 2. **System Prompt 的约束设计**："仅基于参考资料回答"这句话是整个 RAG 防幻觉的核心，没有这一句约束，LLM 可能无视检索结果自己编答案。
> 3. **Java 和 Python 的 DTO 契约**：两边各自定义模型类（Java DTO ↔ Python Pydantic），字段名用 JSON 驼峰风格对齐，FastAPI 自动做请求校验和响应序列化。

**Q26：如果重新设计，你会怎么做？**

> 1. 把 `executeQaCore()` 中的 Prompt 拼接提取为一个独立的 `PromptBuilder` 类，方便单测和 Prompt 模板化
> 2. Python 端加统一的错误响应格式（目前 HTTP 500 和正常响应结构不一致，Java 端需要额外处理 HttpServerErrorException）
> 3. 加一个 `QA_STRATEGY` 接口，允许同步/异步/缓存等不同策略切换

**Q27：你对 RAG 的理解是什么？**

> RAG = Retrieval-Augmented Generation，检索增强生成。把"检索"和"生成"分开：
> 1. **检索**：用户问题 → 向量化 → 在知识库里做相似度搜索 → 找到最相关的文档片段
> 2. **增强**：把检索到的片段拼到 Prompt 里，作为"参考资料"给 LLM
> 3. **生成**：LLM 基于参考资料生成答案，引用来源
>
> 对比纯 LLM，RAG 能给出有来源可追溯的答案，避免幻觉，也能让模型回答它训练数据中没有的企业内部知识。

**Q28：这个项目涉及的技术栈有哪些？**

> **Java 后端**：Spring Boot、Spring MVC（REST API）、MyBatis、MySQL、Redis（限流 + token 存储）、JWT（jjwt 库）、RabbitMQ（代码已备）、WebSocket（代码已备）、RestTemplate（HTTP 客户端）
>
> **Python RAG 服务**：FastAPI、LangChain、ChromaDB、DashScopeEmbeddings（text-embedding-v4）、Uvicorn
>
> **LLM**：阿里云 DashScope（Qwen 系列，兼容 OpenAI API 格式）
>
> **部署**：Java 端口 8080、Python 端口 8001、Redis 6379、MySQL 3306、开发环境 Windows

---

## 六、面试现场口述模板

### 1 分钟项目介绍

> 我做的项目是企业知识库智能问答平台。员工把公司文档上传后，可以在聊天框里用自然语言提问，系统通过 RAG 技术从知识库中检索相关内容，再用大模型生成有来源依据的答案，避免模型编造信息。
>
> 项目是 Java + Python 服务化架构。Java Spring Boot 负责用户认证、限流、编排调度和持久化；Python FastAPI 作为独立的 RAG 检索引擎，用 LangChain + ChromaDB 做文档向量化和相似度搜索。两边通过 HTTP 解耦。
>
> 我负责 Java 后端部分，实现了 JWT 登录鉴权、Redis 用户级限流、调用 Python RAG 服务、构造 Prompt 调用 LLM、MySQL 问答历史追踪，以及每个环节的异常兜底处理。

---

### 3 分钟项目介绍

> 我做的项目是一个**企业知识库智能问答平台**，用 RAG 技术让员工能基于公司内部文档提问，获得有来源可追溯的答案。项目采用 Java Spring Boot + Python FastAPI 的服务化架构。
>
> **先说整体流程**：用户在聊天框输入问题 → Java 后端接收请求，先做 JWT 校验和 Redis 限流 → 通过 HTTP 调用 Python RAG 服务做向量检索 → Python 用 ChromaDB 查相似文档片段返回给 Java → Java 把检索结果拼接成 Prompt → 调用阿里云 DashScope 大模型生成答案 → 最后把问答记录存 MySQL，返回给前端。
>
> **我重点讲三个设计亮点**：
>
> 第一是**异常兜底的层次设计**。RAG 服务挂了返回"检索服务暂时不可用"，检索为空返回"知识库中没有相关信息"且不调 LLM 避免幻觉，LLM 挂了返回"AI 服务异常"。每种情况的错误信息都持久化到 qa_history 表，状态码分别是 FAILED、NO_CONTEXT、FAILED，方便后续排查。
>
> 第二是**限流方案**。在 Service 层用 Redis INCR 做用户级分钟限流，key 设计包含了 userId 和分钟桶。关键是 Redis 不可达时自动放行——不阻塞正常业务。阈值通过配置文件注入，默认每分钟 10 次。
>
> 第三是**服务化拆分**。Python 做它擅长的 RAG 检索（LangChain + ChromaDB + Embedding），Java 做它擅长的 Web 工程（认证、限流、编排、持久化）。Python 完全不需要感知用户、权限和 SQL，Java 也不需要处理文档解析和向量化。两边通过一个 REST API 解耦，后续换向量数据库或换 LLM 供应商只影响一边。
>
> 另外，代码层面已经为异步扩展做好了准备——RabbitMQ 独立队列、消费者、WebSocket 推送都已实现，只需要激活 Profile 就能切到异步模式。qa_history 表用 NO_CONTEXT 状态区分"检索不到"和"系统故障"，方便运营分析哪些知识库内容需要补充。
>
> 这个项目让我理解了**后端作为编排层**的定位——不是简单透传，而是做认证、限流、兜底、持久化这些企业级应用必须做的事情。
