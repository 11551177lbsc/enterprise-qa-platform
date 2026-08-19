# 项目演示稿：企业知识库智能问答平台

> 录屏演示用，按顺序操作，每步有"操作"和"讲什么"。

---

## 第一阶段：环境展示（3 分钟）

### 1.1 展示项目目录结构

**操作**：在 VS Code 中展开项目根目录，用鼠标大致圈一下两个主要模块。

```
enterprise-qa-platform/
├── aiLLL/          ← Java Spring Boot 后端（端口 8080）
└── ragagent/       ← Python FastAPI RAG 服务（端口 8001）
```

**讲什么**：

> 这个项目是企业知识库智能问答平台，采用 Java + Python 服务化架构。
>
> `aiLLL` 是 Java Spring Boot 主后端，负责用户认证、限流、编排调度、LLM 调用和 MySQL 持久化，跑在 8080 端口。
>
> `ragagent` 是 Python FastAPI 服务，只做一件事——文档向量检索，跑在 8001 端口。
>
> 前端只和 Java 通信，Java 再通过 HTTP 调用 Python 的 RAG 接口。这样拆分的好处是：Python 不需要感知用户、权限、SQL，Java 不需要处理文档解析和向量化，两边各做各擅长的。

---

### 1.2 展示知识库数据

**操作**：用 VS Code 展开 `ragagent/data/` 目录，打开 `选购指南.txt` 展示前几行。

**讲什么**：

> 这是知识库的原始文档。当前放了 6 个文件，都是扫地机器人相关的——选购指南、故障排除、维护保养等，一共 200 多条知识。
>
> 这些 txt 和 pdf 文件会在下一步被 Python 服务加载，切分成小片段（每个片段 200 字），通过阿里云的 text-embedding-v4 模型转成向量，存入 ChromaDB。之后用户提问题的时候，系统就能从这些文档里检索到最相关的内容。

---

## 第二阶段：启动服务（5 分钟）

### 2.0 打开三个终端

**操作**：开三个终端窗口，都切到项目目录。

| 终端 | 用途 | 工作目录 |
|------|------|---------|
| 终端 1 | Redis 检查 / 数据库查询 | 任意 |
| 终端 2 | Python RAG 服务 (端口 8001) | `E:\enterprise-qa-platform\ragagent` |
| 终端 3 | Java Spring Boot (端口 8080) | `E:\enterprise-qa-platform\aiLLL` |

---

### 2.1 检查远程 Redis

**操作**：终端 1，ping 远程 Redis（地址在 `application.yml` 第 7-8 行配置的 `192.168.100.128:6379`）。

```bash
redis-cli -h 192.168.100.128 -p 6379 ping
```

预期输出：`PONG`

**讲什么**：

> Redis 部署在远程虚拟机 `192.168.100.128` 上，先确认网络是通的。Redis 在这个项目里有两个作用：一是存储 JWT 登录 token（key 格式 `login:{userId}`），二是做用户级问答限流（key 格式 `qa:rate:user:{userId}:{分钟桶}`）。

---

### 2.2 确认本地 MySQL

**操作**：终端 1，连接本地 MySQL 确认 `ailll` 库和 `qa_history` 表存在。

```bash
mysql -u root -p1234 -e "USE ailll; DESC qa_history;"
```

**讲什么**：

> MySQL 是本地的，数据库叫 `ailll`，密码 1234。核心表是 `qa_history`——记录每次问答的完整生命周期。字段包括 user_id、session_id、question、answer、references_json、status（SUCCESS / NO_CONTEXT / FAILED / PENDING）、error_message、create_time。这是面试会重点问的表。

---

### 2.3 启动 Python RAG 服务（FastAPI，端口 8001）

**操作**：终端 2，激活虚拟环境，启动 FastAPI。

```bash
cd E:\enterprise-qa-platform\ragagent

# 激活虚拟环境（Windows）
.venv\Scripts\activate

# 启动 RAG 服务
python rag_server.py
```

预期输出：
```
INFO:     Started server process [xxxxx]
INFO:     Uvicorn running on http://0.0.0.0:8001
```

**讲什么**：

> Python 项目有自己的虚拟环境 `.venv`，里面装了 fastapi、uvicorn、langchain-chroma、dashscope 等依赖。`rag_server.py` 是一个独立的 FastAPI 服务，暴露三个接口：
>
> - `POST /api/rag/search` —— Java 调用的检索接口，接收 query + topK，返回 chunks
> - `GET /api/rag/health` —— 健康检查，返回 collection 名称和文档数量
> - `POST /api/rag/index` —— 文档入库，扫描 data/ 目录，切分 → 向量化 → 写入 ChromaDB
>
> 启动时会初始化 ChromaDB，连接本地 `chroma_db/` 目录，加载阿里云 text-embedding-v4 作为向量化模型。

**操作**：浏览器打开 `http://localhost:8001/docs`，展示 FastAPI 自动生成的 Swagger 文档。

**讲什么**：

> 这是 FastAPI 自带的好处——自动根据 Pydantic 模型生成接口文档。可以看到 SearchRequest 的结构：knowledgeBaseId、query、topK 三个字段。Java 端的 `RagSearchRequest` DTO 就是按这个契约写的。

**操作**：在 Swagger 上点 `GET /api/rag/health` → Try it out → Execute。

**讲什么**：

> 看一下当前 ChromaDB 里有多少文档。如果是 0 说明还没入库，后面演示时会调 index 接口。

---

### 2.4 启动 Java Spring Boot（端口 8080）

**操作**：终端 3，用 Maven 启动 Spring Boot。

```bash
cd E:\enterprise-qa-platform\aiLLL

# Maven 启动（跳过测试，加速启动）
mvn spring-boot:run -DskipTests
```

或者在 IDE（VS Code）中直接点击 `AilllApplication.java` 的 Run 按钮。

预期输出：
```
Started AilllApplication in X.XXX seconds
```

**讲什么**：

> Java 是 Spring Boot 4.0.3 + Maven 项目，启动在 8080 端口。启动时会自动连接 MySQL（`application.yml` 第 11-14 行配置的 `localhost:3306/ailll`）、Redis（第 7-8 行，`192.168.100.128:6379`），初始化 MyBatis Mapper 和 JWT 拦截器。
>
> 当前稳定版本是同步 RAG 问答——用户发问题，Java 同步等待 RAG 检索 + LLM 完成才返回。RabbitMQ 异步方案代码已备好但默认不启用。

---

## 第三阶段：演示核心流程（10 分钟）

> 用 Apifox 或 Postman 按顺序调接口，边调边讲。

### 3.1 用户注册与登录

**操作**：POST 请求 `http://localhost:8080/auth/register`

```json
{
    "username": "demo_user",
    "password": "123456"
}
```

**讲什么**：

> 先注册一个测试账号。

---

**操作**：POST 请求 `http://localhost:8080/auth/login`

```json
{
    "username": "demo_user",
    "password": "123456"
}
```

**讲什么**：

> 登录成功会返回 JWT token 和 userId。这个 token 的生成过程是：`JwtUtil.generateToken(userId)`，用 HMAC-SHA256 签名，subject 存 userId，24 小时过期。同时 token 会存入 Redis，key 是 `login:{userId}`。
>
> 后续所有需要认证的接口，都要在 Header 里带上 `Authorization: Bearer {token}`。拦截器 `JwtInterceptor` 会从 Header 中取出 token，解析出 userId，再去 Redis 验证这个 token 是否存在。

**操作**：记下返回的 token 和 userId，后续请求要用。

---

### 3.2 查看文档索引（第一次演示时可能需要先入库）

**操作**：在 Swagger `http://localhost:8001/docs` 上点击 `POST /api/rag/index` → Execute。

或者在终端用 curl：
```bash
curl -X POST http://localhost:8001/api/rag/index
```

**讲什么**：

> 如果知识库还是空的，先调一下文档入库接口。Python 会扫描 `data/` 目录下的所有 txt 和 pdf 文件，对每个文件计算 MD5 做去重，然后把内容切分成 200 字一片的 chunk，调用阿里云 text-embedding-v4 转成向量，写入 ChromaDB。
>
> MD5 去重的意思是——如果这个文件之前已经入库过，这次就跳过，不会重复向量化。

**操作**：再调一次 `GET /api/rag/health`，展示文档数量已增加。

---

### 3.3 同步 RAG 问答 — 核心演示

**操作**：POST 请求 `http://localhost:8080/api/chat/ask`

Header：
```
Authorization: Bearer {刚才拿到的token}
Content-Type: application/json
```

Body：
```json
{
    "knowledgeBaseId": "agent",
    "sessionId": "demo_session_001",
    "question": "家里有宠物，买扫地机器人要注意什么？"
}
```

**讲什么**：

> 这是整个项目最核心的接口。我发一个问题："家里有宠物，买扫地机器人要注意什么？"
>
> 接下来我对照代码讲一下这个请求背后发生了什么——

**操作**：切换到 VS Code，打开 `KnowledgeQaController.java` 第 33 行。

**讲什么**：

> 请求到达 Controller 的 `ask()` 方法。先通过 `extractUserId(token)` 从 JWT 的 Authorization Header 里解析出 userId。然后调 Service 的 `ask()` 方法。

**操作**：打开 `KnowledgeQaServiceImpl.java`，定位到第 153 行 `ask()` 方法。

**讲什么**：

> Service 层首先做 **Redis 限流**。看第 227 行的 `checkRateLimit()` 方法。key 设计为 `qa:rate:user:{userId}:{分钟桶}`，用 Redis INCR 原子自增计数。超过阈值（默认每分钟 10 次）就返回"请求过于频繁"。如果 Redis 挂了，catch 异常直接放行——不阻塞正常业务。

**操作**：打开 `KnowledgeQaServiceImpl.java`，定位到第 81 行 `executeQaCore()` 方法。

**讲什么**：

> 限流通过后进入核心流程 `executeQaCore()`。这个方法同步和异步路径共用，是整条链路最关键的代码。
>
> **Step 1** —— 调 `ragSearchClient.search()` 发 HTTP POST 到 Python 的 `/api/rag/search`，传查询词和 topK=3。

**操作**：打开 `RagSearchClient.java` 第 33 行。

**讲什么**：

> 这是 Java 和 Python 的桥梁。用 RestTemplate 发 POST 请求，Python 地址通过配置文件 `rag.base-url` 注入，默认 `http://localhost:8001`。如果 Python 服务挂了——连接拒绝、超时、500——都会被 catch 住，返回一个带 error 字段的 `RagSearchResponse`，而不是向上抛异常。

**操作**：打开 `rag_server.py` 第 101 行。

**讲什么**：

> Python 端收到请求后，调用 ChromaDB 的 `similarity_search_with_score()` 做向量相似度搜索。内部流程是：先把 query 用 text-embedding-v4 转成向量，然后在 ChromaDB 中找 L2 距离最近的 top-3 文档片段。L2 距离通过 `1/(1+distance)` 转换为 0~1 的相似度分数，按分数降序返回。

**操作**：回到 `KnowledgeQaServiceImpl.java`，定位到第 91-143 行，展示三层兜底逻辑。

**讲什么**：

> Java 拿到检索结果后，有三层兜底处理：
>
> **第一层**：如果 `error` 字段不为空，说明 Python 服务不可用，直接返回"知识库检索服务暂时不可用"，status 设为 FAILED。
>
> **第二层**：如果 chunks 为空，说明知识库中没有相关内容，返回"抱歉，当前知识库中没有找到与您问题相关的信息"，status 设为 NO_CONTEXT。**注意这里不会调用 LLM**——这是 RAG 防幻觉的关键设计，不能把空上下文喂给模型让它编答案。
>
> **第三层**：检索正常，拼 Prompt。看第 120 行，把每个 chunk 格式化成 `【参考资料1】(来源: 选购指南.txt): 内容...`。System Prompt 明确约束"仅基于参考资料回答，没有就说不知道"。然后调 `AiClient.chatWithSystem()`。

**操作**：打开 `AiClient.java` 第 85 行。

**讲什么**：

> AiClient 用 OpenAI 兼容格式调阿里云 DashScope。构造 system + user 两条消息，POST 到 `/chat/completions`。LLM 返回后解析 `choices[0].message.content`。如果解析失败或返回空，上层 Service 会 catch 并返回"AI 服务返回异常"。

**操作**：回到 Apifox 展示响应结果。

预期响应：
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "sessionId": "demo_session_001",
        "answer": "根据选购指南，带宠物的家庭选购扫地机器人需重点关注以下几点：...",
        "references": [
            {"source": "选购指南.txt", "chunkId": "abc123...", "score": 0.85},
            {"source": "维护保养.txt", "chunkId": "def456...", "score": 0.72}
        ]
    }
}
```

**讲什么**：

> 返回的结果包含三部分：answer 是 LLM 基于知识库生成的答案——你可以看到它引用了【参考资料1】；references 是引用的来源文档片段，包含文件名和相似度分数，用户可以追溯信息来源。
>
> 同时这条问答记录已经被持久化到 MySQL 的 qa_history 表，status 是 SUCCESS。

---

### 3.4 展示异常兜底（3 个场景）

#### 场景 A：检索为空

**操作**：发一个知识库中不可能有答案的问题。

```json
{
    "knowledgeBaseId": "agent",
    "sessionId": "demo_session_001",
    "question": "公司年假怎么申请？"
}
```

**讲什么**：

> 知识库里全是扫地机器人的内容，问"公司年假怎么申请"肯定检索不到。系统返回"抱歉，当前知识库中没有找到与您问题相关的信息"。注意它不会去调 LLM——避免了模型在没有上下文的情况下编一个假的年假政策出来。

#### 场景 B：Python 服务不可用（可选）

**操作**：停掉 Python 服务（Ctrl+C），再发一次正常问题。

**讲什么**：

> 我把 Python 服务停了。再发一次请求——Java 不会崩，`RagSearchClient` catch 到了 Connection Refused 异常，返回"知识库检索服务暂时不可用，请稍后重试"。这就是我之前讲的优雅降级。

**操作**：重新启动 Python 服务。

#### 场景 C：限流触发

**操作**：用同一个 token 短时间内连续发 11 次请求。

**讲什么**：

> 连续发 11 次请求，第 11 次会触发限流——返回"请求过于频繁，请稍后再试（每分钟最多 10 次）"。这个阈值可以在 `application.yml` 的 `qa.rate-limit.max-per-minute` 配置。

---

### 3.5 展示 qa_history 表

**操作**：用 Navicat/DBeaver 或命令行查询 qa_history 表。

```sql
SELECT id, user_id, session_id, LEFT(question, 30) AS question, 
       LEFT(answer, 50) AS answer, status, create_time 
FROM qa_history 
ORDER BY create_time DESC 
LIMIT 5;
```

**讲什么**：

> 每次问答都会被记录到这张表。可以看到不同 status 的记录：SUCCESS 表示正常完成了 RAG+LLM；NO_CONTEXT 表示检索为空没有调 LLM。error_message 字段记录了失败原因。references_json 存的是引用的文档来源列表。这张表可以按 user_id 查用户历史、按 session_id 查一轮对话，支持后续的数据分析。

---

## 第四阶段：代码走读精选（5 分钟）

> 选 3-4 个关键文件快速过一下核心代码，不要太细。

### 4.1 JWT 拦截器

**操作**：打开 `JwtInterceptor.java`。

**讲什么**：

> 这是我写的 JWT 认证拦截器，实现 `HandlerInterceptor` 接口。所有请求在进入 Controller 之前会先过 `preHandle()` 方法。流程是：取 Header 中的 Authorization → 去掉 "Bearer " 前缀 → JWT 解析出 userId → 去 Redis 查 `login:{userId}` 这个 key 是否存在。
>
> 三种失败情况：token 为空返回 401 "未登录"、JWT 解析失败返回 "token无效"、Redis 中不存在返回 "登录过期"。OPTIONS 预检请求直接放行。

### 4.2 Redis 限流

**操作**：打开 `KnowledgeQaServiceImpl.java` 第 227 行 `checkRateLimit()`。

**讲什么**：

> 限流用了一个很常用的设计——滑动分钟窗口。`currentTimeMillis / 60000` 算出当前在哪一分钟，作为 key 的一部分。Redis INCR 原子自增，首次设置为 1 分钟过期，这样每分钟自动重置。关键是 catch 异常时直接 return true 放行——这是限流设计中的一个重要原则：限流组件不能成为单点故障，挂了就降级放行。

### 4.3 RAG 检索 + 异常兜底

**操作**：打开 `KnowledgeQaServiceImpl.java` 第 81-146 行 `executeQaCore()`。

**讲什么**：

> 这是整个项目的"心脏"——所有 RAG QA 请求最终都走这个方法。不到 70 行代码，但包含了四层逻辑：RAG 检索、空结果处理、Prompt 构造、LLM 调用和兜底。每层都有对应的异常处理，每种异常都有明确的用户提示和状态码。这体现了企业级应用和 demo 的最大区别——不是只能跑通 sunny day，而是 rainy day 时也能优雅降级。

### 4.4 ChromaDB 向量检索

**操作**：打开 `rag_server.py` 第 101 行，以及 `vector_store.py` 第 14 行。

**讲什么**：

> Python 端的核心就两个文件：`rag_server.py` 定义 FastAPI 接口，`vector_store.py` 封装 ChromaDB。`VectorStoreService` 初始化时传入 collection 名称、embedding 函数和持久化目录，然后 `similarity_search_with_score()` 一行代码完成向量检索。L2 距离到相似度的转换我单独抽了一个函数 `_l2_to_similarity`，公式是 `1/(1+distance)`，保证返回给 Java 的是 0 到 1 的语义相似度分数。

---

## 第五阶段：总结收尾（2 分钟）

**操作**：回到项目根目录或架构图。

**讲什么**：

> 总结一下。这个项目的核心思路是 **Java 做编排，Python 做检索**。
>
> 完整的请求链路是：前端 → Controller → JWT 认证 → Redis 限流 → Java 调 Python RAG 服务 → ChromaDB 向量检索 → 检索结果拼 Prompt → 调阿里云 LLM → 存 MySQL → 返回。
>
> 我认为面试中值得强调的几个技术点：
> 1. **三层异常兜底**：RAG 不可用、检索为空、LLM 失败，每种都有不同处理策略
> 2. **限流设计**：Redis INCR + 分钟桶，Redis 挂了自动放行
> 3. **qa_history 状态追踪**：不是只记录成功，NO_CONTEXT 和 FAILED 同样重要
> 4. **服务化拆分**：Python 做 RAG 不需要感知用户和权限，Java 做编排不需要处理向量化
> 5. **扩展准备**：RabbitMQ 异步队列、WebSocket 推送代码已就绪，切 Profile 即可激活
>
> 这个项目让我理解了后端不是简单的 CRUD——作为编排层，要处理好认证、限流、调度、异常兜底、状态追踪这些企业级应用的关键问题。

---

## 附录 A：演示前检查清单

### 软件与服务
- [ ] **远程 Redis** 连通：`redis-cli -h 192.168.100.128 -p 6379 ping` → `PONG`
- [ ] **本地 MySQL** 已启动，`ailll` 库存在：`mysql -u root -p1234 -e "USE ailll; SHOW TABLES;"`
- [ ] **Python 虚拟环境** 就绪：`E:\enterprise-qa-platform\ragagent\.venv\Scripts\activate`
- [ ] **Java Maven** 编译通过：`cd E:\enterprise-qa-platform\aiLLL && mvn compile -DskipTests`
- [ ] **Apifox / Postman** 已打开
- [ ] **VS Code** 已打开项目 `E:\enterprise-qa-platform`

### 配置文件
- [ ] `application.yml` 配置确认：Redis `192.168.100.128:6379`、MySQL `localhost:3306`、RAG `localhost:8001`、LLM API Key
- [ ] `ragagent/data/` 下有文档：选购指南.txt、故障排除.txt、维护保养.txt 等 6 个文件
- [ ] `ragagent/chroma_db/` 目录存在（首次需执行 index）

### 启动命令速查

```
# 终端 1：Redis 检查 / MySQL 查询
redis-cli -h 192.168.100.128 -p 6379 ping
mysql -u root -p1234 -e "USE ailll; DESC qa_history;"

# 终端 2：Python RAG 服务（8001）
cd E:\enterprise-qa-platform\ragagent
.venv\Scripts\activate
python rag_server.py

# 终端 3：Java Spring Boot（8080）
cd E:\enterprise-qa-platform\aiLLL
mvn spring-boot:run -DskipTests
```

### 演示接口顺序

| 步骤 | 接口 | 说明 |
|------|------|------|
| 1 | `POST localhost:8080/auth/register` | 注册（`{"username":"demo","password":"123456"}`） |
| 2 | `POST localhost:8080/auth/login` | 登录，拿到 token |
| 3 | `GET localhost:8001/api/rag/health` | 确认 Python 服务正常 |
| 4 | `POST localhost:8001/api/rag/index` | 文档入库（首次必须，之后可跳过） |
| 5 | `POST localhost:8080/api/chat/ask` | **核心演示** RAG 问答 |
| 6 | 同上，换问题测试空检索 | "公司年假怎么申请？"（知识库中不存在） |
| 7 | 短时间连续发 11 次 | 演示限流触发 |
| 8 | 停掉 Python 再发一次 | 演示 RAG 服务不可用时的优雅降级 |
| 9 | `SELECT * FROM qa_history ORDER BY create_time DESC LIMIT 5` | 展示持久化记录 |
