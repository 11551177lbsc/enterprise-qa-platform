# 项目演示讲解稿

> 录屏用，照着念。`【操作】` 是你录屏时要做的，其余是口述内容。

---

## 开场（30秒）

大家好，我做的是一个企业知识库智能问答平台。它解决的核心问题是：员工上传公司内部文档后，可以直接用自然语言提问，得到有来源依据的答案，而不是让 AI 乱编。

技术栈是 Java Spring Boot 做主后端，Python FastAPI 做独立的 RAG 检索服务，两边通过 HTTP 解耦。下面我从架构、启动、代码、演示、亮点五个部分来讲。

---

## 一、架构概览（2分钟）

**【操作】展开项目目录，鼠标圈 aiLLL 和 ragagent 两个文件夹**

项目分两个模块。上面 `aiLLL` 是 Java Spring Boot，跑在 8080 端口，负责用户认证、限流、编排调度、LLM 调用和 MySQL 持久化。下面 `ragagent` 是 Python FastAPI，跑在 8001 端口，只做一件事——把用户问题转成向量，在 ChromaDB 里搜最相关的文档片段。

**【操作】打开 application.yml，鼠标圈 rag.base-url: http://localhost:8001**

Java 怎么调 Python？看配置文件的第 38 行：`rag.base-url: http://localhost:8001`。Java 通过 `RagSearchClient` 发 HTTP POST 到 `/api/rag/search`，Python 返回检索到的文档片段，Java 再拼 Prompt 调 LLM。

**【操作】打开 KnowledgeQaServiceImpl.java，往下滚到 executeQaCore 方法**

整个流程的入口是 `POST /api/chat/ask`。请求进来后走 6 步：JWT 认证 → Redis 限流 → 调 Python RAG 检索 → 拼 Prompt → 调 LLM → 存 MySQL 返回。

这 6 步中我重点讲 3 个地方——RAG 检索的桥接、三层异常兜底、还有 qa_history 的状态追踪。

---

## 二、核心链路走读（6分钟）

**【操作】打开 KnowledgeQaController.java 第33行**

请求先到 Controller 的 `ask()` 方法。这里做两件事：从 Authorization Header 里解析 JWT，拿到 userId；然后委托给 Service 层处理。Controller 不写业务逻辑，只做路由和参数提取。

**【操作】打开 JwtInterceptor.java，展示 preHandle 方法**

JWT 验证不是在 Controller 里做的，而是一个独立的拦截器 `JwtInterceptor`，实现了 Spring 的 `HandlerInterceptor` 接口。所有请求在进 Controller 之前会先过 `preHandle()`。流程是：取 Header 中的 Authorization → 去掉 "Bearer " 前缀 → 用 jjwt 库解析出 userId → 去 Redis 查 `login:{userId}` 这个 key 是否存在。

三种失败情况都返回 401：token 为空返回"未登录"，JWT 签名不对返回"token无效"，Redis 中不存在返回"登录过期"。这里有一个细节——JWT 解析通过只是说明 token 格式合法，Redis 中 key 存在才算真正有效，这样服务端可以主动删除 key 实现强制踢人。

**【操作】打开 KnowledgeQaServiceImpl.java，定位到 ask() 方法第153行**

Service 的 `ask()` 方法先做 Redis 限流。

**【操作】往下滚到 checkRateLimit() 第227行**

限流 key 的设计是 `qa:rate:user:{userId}:{分钟桶}`。分钟桶怎么算？`System.currentTimeMillis() / 60000`——当前毫秒时间戳除 6 万，得到当前是第几分钟。Redis INCR 原子自增，首次设为 1 分钟过期。阈值通过 `@Value` 注解从配置文件注入，默认每分钟 10 次。

这里有一个非常重要的设计——看第 238 行的 catch 块：如果 Redis 挂了，catch 异常直接 `return true` 放行。这背后的原则是：限流组件不能成为单点故障，挂了就降级放行，不能因为限流把正常业务也挡住。

**【操作】回到 executeQaCore() 第81行**

限流通过后进入 `executeQaCore()`，这是整条链路最核心的方法，同步和异步路径共用。

**第一步**，第 87 行，调 `ragSearchClient.search()` 发 HTTP 到 Python RAG 服务。

**【操作】打开 RagSearchClient.java 第33行**

`RagSearchClient` 是一个被 `@Component` 注解的 Spring Bean，用 RestTemplate 发 POST 请求。URL 是从 `RagConfig` 注入的 `rag.base-url + /api/rag/search`。重点看异常处理——第 48 行的 catch 块：任何 HTTP 异常（连接拒绝、超时、500）都被 catch 住，返回一个带 error 字段的 `RagSearchResponse`，而不是向上抛异常。这样上层只需要检查 `response.getError()` 是否为空。

**【操作】打开 rag_server.py 第101行**

Python 端收到请求后，调 ChromaDB 的 `similarity_search_with_score()`。ChromaDB 内部自动把 query 用 text-embedding-v4 转成向量，做 L2 距离检索。返回的距离通过 `1/(1+distance)` 转换为 0 到 1 的相似度分数——1 是完全匹配，接近 0 是不相关。按分数降序返回 top-3 片段。

**【操作】回到 executeQaCore()，从第91行往下展示三个 if-else 分支**

这里就是整个项目最值得讲的**三层异常兜底**：

第 91 行——如果 Python 返回的 `error` 字段不为空，说明 RAG 服务不可用。status 设为 FAILED，返回"知识库检索服务暂时不可用"。

第 99 行——如果 chunks 为空，说明知识库中没有相关内容。status 设为 NO_CONTEXT，返回"抱歉，当前知识库中没有找到与您问题相关的信息"。**注意这里不会调 LLM**——这是 RAG 防幻觉的关键，不能把空上下文喂给模型让它编答案。

第 120 行——检索正常的话，把每个 chunk 格式化成 `【参考资料1】(来源: 选购指南.txt): 内容...`。System Prompt 约束模型"仅基于参考资料回答，没有就说不知道"，这是防止模型忽略检索结果自己编造信息的第二道防线。

**【操作】打开 AiClient.java 第85行**

然后调 `AiClient.chatWithSystem()`。我们用 OpenAI 兼容格式——system + user 两条消息，POST 到阿里云 DashScope 的 `/chat/completions`。LLM 配置通过 `llm.*` 注入，支持环境变量覆盖 API Key。

最后回到 Service 把问答记录存 MySQL，返回给前端。

---

## 三、现场演示（5分钟）

**【操作】切换到终端，展示三个终端窗口**

我先演示一下启动过程。我已经开了三个终端——第一个用来查 Redis 和 MySQL，第二个跑 Python RAG 服务，第三个跑 Java。

**【操作】终端1：redis-cli ping，mysql 查 qa_history 表结构**

Redis 是远程虚拟机的，`PONG` 说明通着。MySQL 是本地的，`qa_history` 表字段包括 user_id、session_id、question、answer、references_json、status、error_message。

**【操作】终端2：激活虚拟环境，python rag_server.py**

Python 有自己的虚拟环境 `.venv`，里面装了 fastapi、langchain-chroma、dashscope 等。启动 Rag Server，看到 Uvicorn running on 8001 就说明起来了。

**【操作】浏览器打开 localhost:8001/docs，展示 Swagger 页面**

FastAPI 自动生成了 Swagger 文档，可以看到三个接口。我先点健康检查看一下——collection 名 agent，里面已经有文档了，之前入库过。

**【操作】终端3 或 IDE 运行 AilllApplication**

Java 用 Maven 启动，跑在 8080 端口。Spring Boot 启动日志里可以看到连接了 MySQL、Redis。

**【操作】切到 Apifox**

接下来演示核心流程。

**【操作】发注册请求 → 发登录请求 → 拿到 token**

先注册，再登录。登录返回 JWT token 和 userId。这个 token 后续每个请求都要带在 Authorization Header 里。

**【操作】发 RAG 问答请求：问题"家里有宠物，买扫地机器人要注意什么？"**

这是核心演示。我发一个和知识库内容相关的问题："家里有宠物，买扫地机器人要注意什么？"知识库里有 200 多条选购指南。

**【操作】展示响应结果**

返回的结果里 answer 是 LLM 基于知识库生成的答案，引用了【参考资料1】，references 里可以看到来源文件是"选购指南.txt"，相似度 0.85。用户知道这个答案有据可查。

**【操作】发空检索请求：问题"公司年假怎么申请？"**

知识库里全是扫地机器人的内容，问"公司年假怎么申请"肯定查不到。系统返回"抱歉，当前知识库中没有找到与您问题相关的信息"。注意它没有调 LLM——就是我前面说的 NO_CONTEXT 分支。

**【操作】连续发 11 次请求，展示限流触发**

刷到第 11 次触发限流——"请求过于频繁，请稍后再试"。

**【操作】停掉 Python 服务（Ctrl+C），再发一次请求**

最后演示优雅降级。把 Python 停了，再发一次请求。Java 没有崩，返回"知识库检索服务暂时不可用"。

**【操作】重新启动 Python 服务**

**【操作】mysql 查 qa_history 表**

看一下数据库。qa_history 里能看到每一笔记录：SUCCESS 的有完整答案，NO_CONTEXT 的只有提示信息，FAILED 的有 error_message。每笔都有 status 标记，方便后续排查和分析。

---

## 四、总结：这个项目我会重点讲的技术亮点（2分钟）

**【操作】边讲边切到对应代码文件，点到为止，不用细看**

**第一个亮点：分层架构 + 服务化拆分。**

Controller 做路由，Service 做编排，Client 封装外部调用。Python 做 RAG 检索不需要知道用户是谁、有没有权限——它只接收查询词返回文档片段。Java 做业务编排不需要关心文档怎么向量化、ChromaDB 怎么配置。两边通过一个 REST 接口解耦，后续换向量数据库只改 Python，换 LLM 供应商只改 Java。

**第二个亮点：三层异常兜底，每层有独立的状态追踪。**

RAG 不可用 → FAILED → "检索服务暂时不可用"；检索为空 → NO_CONTEXT → "知识库中没有相关信息"，不调 LLM；LLM 失败 → FAILED → "AI 服务返回异常"。三种场景三种处理，每种都在 MySQL 里有明确的状态码和错误信息。这和那种"出错就 500"的 demo 有本质区别。

**第三个亮点：Redis 限流的降级设计。**

限流 key 按用户 + 分钟桶隔离，INCR 原子自增，配置化阈值。关键是 Redis 挂了自动放行——限流组件不能成为单点故障。

**第四个亮点：qa_history 的状态追踪不是只记成功。**

SUCCESS / NO_CONTEXT / FAILED / PENDING 四种状态，error_message 记录失败原因，references_json 保留引用溯源。可以按 user_id 查用户历史、按 session_id 查一轮对话，给后续的数据分析和运营优化留了基础。

**第五个亮点：扩展性准备。**

RabbitMQ 独立队列 `qa.rag.task.queue`、`QaTaskProducer`、`QaTaskConsumer`、`QaWebSocketPushService` 代码都已就绪。activate mq profile 就能切到异步模式——请求入队立即返回 taskId，后台消费处理完后 WebSocket 推送结果。但当前稳定版本用同步就够了，不需要为了技术花哨硬上异步。

---

## 五、收尾（30秒）

这个项目让我理解了 Java 后端在企业级应用中的定位——不是简单 CRUD 和透传，而是做认证、限流、编排、兜底、状态追踪这些真正保障系统可靠性的工作。谢谢。
