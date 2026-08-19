# 面试问答：企业知识库智能问答平台

## 1. 介绍一下这个项目的整体架构。

这是一个企业知识库智能问答平台，采用 Java Spring Boot 作为主后端，Python FastAPI 作为独立的 RAG 检索服务。

- 前端或 Apifox 只和 Java 后端通信（端口 8080），不直接访问 Python 服务。
- Java 后端负责用户认证（JWT + Redis）、接口编排、Redis 用户级限流、MySQL 持久化、Prompt 构造和 LLM API 调用。
- Python 服务（端口 8001）只做文档向量检索引擎：接收查询词 → ChromaDB 相似度检索 → 返回 Top-K 文档片段。Python 端不处理用户、会话、权限和前端页面。
- LLM 用的是阿里云 DashScope 的 Qwen 系列模型，Embedding 用的是 text-embedding-v4。

## 2. 为什么不让前端直接调用 Python RAG 服务？

主要是安全和职责分离的考虑：

1. 前端直接调 Python 意味着暴露内网服务端口，增加了攻击面。
2. Python 服务没有用户认证和限流能力，直接暴露会被滥用。
3. Java 后端在 RAG 检索结果的基础上还要做 Prompt 拼接、LLM 调用、结果持久化、异常兜底等编排工作，这些逻辑放在 Java 层更合适，前端不需要感知底层服务拆分。
4. 后期如果需要切换向量数据库或调整检索策略，前端不受任何影响。

## 3. Redis 限流是怎么实现的？

在 `KnowledgeQaServiceImpl` 中做了用户级限流：

- 限流 Key 格式：`qa:rate:user:{userId}:{minuteBucket}`
- 使用 Redis `INCR` 命令对当前分钟窗口计数，首次设置 1 分钟过期
- 阈值从配置文件读取（`qa.rate-limit.max-per-minute`，默认 10 次/分钟）
- 如果 Redis 不可达，限流自动放行，不影响主流程
- 超出限制时直接返回友好错误信息，不调用 RAG 和 LLM，也不计入 qa_history

限流粒度是**用户级**：如果能从 JWT 中解析出 userId，就按 userId 隔离；解析不到 userId 时降级为匿名用户统一计数。

## 4. MySQL qa_history 表保存什么？

`qa_history` 表用于持久化每次问答的完整记录：

| 字段 | 说明 |
|------|------|
| `id` | 自增主键 |
| `user_id` | 从 JWT 解析的用户ID（匿名用户为 NULL） |
| `session_id` | 会话ID，同一 session 的问题归为一轮对话 |
| `question` | 用户原始问题 |
| `answer` | LLM 返回的回答 |
| `references_json` | 引用来源的 JSON 数组，含 source / chunkId / score |
| `status` | 处理状态：`SUCCESS` / `NO_CONTEXT` / `FAILED` / `PENDING` |
| `error_message` | 失败时的错误信息 |
| `create_time` / `update_time` | 创建和更新时间 |

这样做的好处是：可以按用户、按会话、按状态查询历史记录，也便于后续做数据分析（如哪些问题经常找不到答案、哪些知识库文档被引用最多）。

## 5. RAG 检索为空时怎么处理？

分两种情况：

1. **Python RAG 服务不可达**（连接超时或返回 error）：status 设为 `FAILED`，返回"知识库检索服务暂时不可用"，同时记录 error_message。

2. **检索成功但无结果**（知识库中没有相关内容）：status 设为 `NO_CONTEXT`，返回"抱歉，当前知识库中没有找到与您问题相关的信息"。此时不会调用 LLM，避免模型在没有上下文的情况下产生幻觉回答。

两种情况都会持久化到 `qa_history` 表，方便后续排查和分析。

## 6. LLM 调用失败怎么处理？

`AiClient.chatWithSystem()` 中如果发生异常或返回空/解析失败：

- 捕获异常，status 设为 `FAILED`
- 返回兜底回答"AI 服务返回异常，请稍后重试"
- error_message 记录具体的异常信息（如"LLM returned empty or parse error"）
- 持久化到 `qa_history`
- `references` 字段在有检索结果但 LLM 失败时仍保留（因为检索本身是成功的），如果是系统级异常则清空

## 7. RabbitMQ + WebSocket 为什么暂时不做，后续怎么扩展？

当前稳定版本是同步问答接口 `POST /api/chat/ask`，已经能满足知识库问答的核心需求。暂时不做 MQ 异步化的原因：

1. 同步接口的调用链路已经完整验证：JWT 校验 → 限流 → RAG 检索 → LLM 调用 → 持久化 → 返回，核心流程没有明显性能瓶颈。
2. 同步模式调试更方便，排查问题更快。
3. MQ 异步化主要适用于高并发场景或需要离线排队处理的场景，当前阶段不需要。

后续扩展方案代码已就绪：

- 新增了独立队列 `qa.rag.task.queue`（不复用旧的 `ai.chat.queue`）
- `QaTaskProducer` 投递任务消息，`QaTaskConsumer` 消费后执行 RAG + LLM 并更新 DB
- `QaWebSocketPushService` 通过 WebSocket 推送 JSON 结果给客户端
- 通过 `application-mq.yml` profile 即可启用

扩展时只需加 Spring Profile 和启动 RabbitMQ，不需要改业务代码。

## 8. 项目中有哪些难点和收获？

**难点**：

- Java 和 Python 是两个独立进程，通过 HTTP 通信，需要做好异常兜底（Python 挂了不影响 Java 返回友好错误）
- ChromaDB 返回的是 L2 distance，需要转换为认知友好的 similarity score（`1/(1+d)`），并且保证排序语义正确
- RAG 检索为空的场景需要特殊处理：不能把空上下文喂给 LLM 生成幻觉回答
- 多 Profile 配置管理：dev / mq / 默认生产配置，确保不同场景下行为正确

**收获**：

- 理解了 Java 作为编排层的职责：不是简单透传，而是做限流、兜底、持久化、Prompt 构造这些应用层工作
- 服务化拆分的实践：Python 做它擅长的向量检索，Java 做它擅长的 Web 工程，通过 HTTP 解耦
- MyBatis + Redis + JWT 这些 Spring Boot 生态组件的实际使用，不是 demo 级别的简单集成
