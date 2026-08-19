# 简历项目描述

> 以下内容可直接用于 Java 后端实习岗位的简历项目经历。建议替换 `[ ]` 中的占位符为实际数据。

---

**企业知识库智能问答平台** | Java 后端开发 | [2026.03] – [2026.06]

- 基于 Spring Boot 4.0 + MyBatis + MySQL 搭建后端服务，实现用户注册登录（JWT + BCrypt）、知识库问答、问答历史持久化等 REST API
- 使用 Redis 实现 JWT token 存储和用户级问答限流（每分钟 N 次，可配置），限流粒度按 userId 隔离，Redis 不可达时自动降级放行
- 独立设计 Java ↔ Python 服务化拆分方案：Python FastAPI 作为无状态 RAG 检索服务（ChromaDB + DashScope Embeddings），Java 通过 RestTemplate HTTP 调用，封装在 client 层，Controller 不接触调用细节
- 在 Service 层完成问答编排：RAG 检索 → 构造 System Prompt + User Prompt（含知识库上下文） → 调用 LLM API（DashScope Qwen） → 返回 answer + references，全程在 MySQL 记录处理状态（SUCCESS / NO_CONTEXT / FAILED）
- 处理多种异常场景：RAG 服务不可达返回降级提示、检索为空返回无结果提示并阻止 LLM 生成幻觉回答、LLM 调用失败记录错误信息并返回兜底回答
- 使用 Spring Profile 管理多环境配置（dev / mq / 默认），dev 环境禁用 RabbitMQ Listener 避免本地开发受 MQ 干扰；保留 RabbitMQ + WebSocket 异步问答扩展方案（独立队列 qa.rag.task.queue，组件已就绪）

---

## 简历关键词建议

Spring Boot、MyBatis、MySQL、Redis、限流、JWT、REST API、Python FastAPI、ChromaDB、RAG 检索、大模型（LLM）、服务化拆分、异常处理、Spring Profile、RabbitMQ、WebSocket
