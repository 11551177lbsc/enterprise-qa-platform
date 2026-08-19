# 企业知识库智能问答平台

Enterprise Knowledge Base Intelligent Q&A Platform

## 项目定位

基于 **Java Spring Boot** 主后端 + **Python FastAPI RAG 检索服务** 的企业级知识库智能问答系统。Java 负责用户鉴权、接口编排、Redis 限流、MySQL 历史记录和 LLM API 调用；Python 仅作为无状态的文档向量检索引擎，不处理用户、会话和权限。

## 架构概览

```
Apifox / Frontend
        │
        ▼
┌──────────────────────────────────────────────────────────────────┐
│              Java Spring Boot 主后端 (port 8080)                  │
│                                                                   │
│  ┌─────────────┐  ┌──────────────────┐  ┌────────────────────┐   │
│  │ /auth/*     │  │ /api/chat/ask    │  │ /api/chat/ask-async│   │
│  │ JWT 认证    │  │ 同步 RAG 问答     │  │ 异步 RAG 问答 (MQ)  │   │
│  └─────────────┘  └───────┬──────────┘  └─────────┬──────────┘   │
│                           │                        │              │
│  ┌────────────────────────┼────────────────────────┼──────────┐   │
│  │ Redis                  │                        │          │   │
│  │ · JWT token 存储       │    MySQL               │          │   │
│  │ · 用户级问答限流        │    · user 表           │          │   │
│  └────────────────────────┤    · qa_history 表     │          │   │
│                           │    · chat_message 表   │          │   │
│                           │                        │          │   │
└───────────────────────────┼────────────────────────┼──────────┘   │
                            │ HTTP (RestTemplate)                   │
                            ▼                                       │
┌──────────────────────────────────────────────────────────────────┐
│          Python FastAPI RAG 服务 (port 8001, 仅内网)              │
│                                                                   │
│  · GET  /api/rag/health    健康检查                               │
│  · POST /api/rag/search    语义检索 (Top-K)                       │
│  · POST /api/rag/index     文档入库                               │
│                         │                                         │
│                   ChromaDB (嵌入式向量库)                          │
│                   DashScope Embeddings                            │
└──────────────────────────────────────────────────────────────────┘
```

## 职责划分

| 组件 | 负责 | 不负责 |
|------|------|--------|
| **Java Spring Boot** | 用户认证、接口编排、Redis 限流、MySQL 持久化、LLM API 调用、Prompt 构造 | 文档向量化、语义检索 |
| **Python FastAPI** | 文档加载、文本分块、向量化、ChromaDB 存储、Top-K 检索 | 用户系统、会话管理、权限控制、前端页面 |
| **MySQL** | user 表、qa_history 问答历史、chat_message 对话记录 | — |
| **Redis** | JWT token 存储、用户级问答限流计数器 | — |
| **ChromaDB** | 文档向量存储和相似度检索 | — |

## 项目结构

```
enterprise-qa-platform/
├── aiLLL/                    # Java Spring Boot 主后端 (Maven, Java 17)
│   ├── src/main/java/com/haust/ailll/
│   │   ├── ai/AiClient.java            # LLM 客户端 (DashScope Qwen)
│   │   ├── client/RagSearchClient.java # RAG 检索 HTTP 客户端
│   │   ├── config/                     # Spring 配置 (JWT/Redis/RabbitMQ/LLM)
│   │   ├── controller/                 # REST 控制器
│   │   │   ├── KnowledgeQaController   # /api/chat/ask + /api/chat/ask-async
│   │   │   ├── AuthController          # /auth/login + /auth/register
│   │   │   └── ChatController          # /chat/stream (SSE 流式对话)
│   │   ├── dto/                        # 请求/响应 DTO
│   │   ├── entity/                     # 数据库实体 (User, ChatMessage, QaHistory)
│   │   ├── interceptor/                # JWT 拦截器 + 限流拦截器
│   │   ├── mapper/                     # MyBatis Mapper
│   │   ├── mq/                         # RabbitMQ 生产者/消费者
│   │   ├── service/                    # 业务服务层
│   │   ├── util/                       # 工具类 (JWT/Redis/Result)
│   │   └── websocket/                  # WebSocket 会话管理
│   └── src/main/resources/
│       ├── application.yml             # 默认配置
│       ├── application-dev.yml         # 本地开发配置 (禁用 MQ)
│       ├── application-mq.yml          # MQ 异步测试配置
│       └── sql/create_qa_history.sql   # 建表 DDL
│
├── ragagent/                 # Python RAG 检索服务
│   ├── rag_server.py                   # FastAPI 入口 (新增)
│   ├── rag/vector_store.py             # ChromaDB 文档存储与检索
│   ├── rag/rag_service.py              # RAG 总结服务 (ReAct Agent 使用)
│   ├── model/factory.py                # LLM + Embedding 工厂
│   ├── data/                           # 知识库文档 (.txt / .pdf)
│   └── config/                         # YAML 配置
│
└── docs/                     # 文档
    ├── startup.md
    ├── api.md
    ├── interview-notes.md
    └── resume-project.md
```

## 当前稳定版本

- **同步问答**: `POST /api/chat/ask` — 完整的 RAG 检索 + LLM 回答 + 历史持久化
- **异步问答**: `POST /api/chat/ask-async` — 投递 RabbitMQ 后立即返回 taskId，WebSocket 推送结果（MQ 组件已就绪，可在 dev,mq profile 下启用）
- 原有简单对话 `/chat/stream`、用户认证 `/auth/*` 保持不变
- Python RAG 服务独立部署在 8001 端口，仅 Java 后端内网调用

## 快速开始

详见 [docs/startup.md](docs/startup.md)

## API 文档

详见 [docs/api.md](docs/api.md)

## 面试问答

详见 [docs/interview-notes.md](docs/interview-notes.md)

## 简历项目描述

详见 [docs/resume-project.md](docs/resume-project.md)
