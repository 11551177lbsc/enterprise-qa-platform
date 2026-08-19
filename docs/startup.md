# 本地启动指南

## 环境要求

| 组件 | 版本 | 用途 |
|------|------|------|
| Java | 17+ | 编译和运行 Spring Boot 项目 |
| Maven | 3.8+ (或使用项目自带 mvnw) | 构建 Java 项目 |
| Python | 3.10+ | 运行 RAG 检索服务 |
| MySQL | 8.0+ | 持久化用户和问答历史 |
| Redis | 6.0+ | JWT 存储和限流计数器 |
| DashScope API Key | — | 阿里云百炼平台 (LLM + Embedding) |

> RabbitMQ 在同步开发模式下不需要，已通过 `application-dev.yml` 禁用。

---

## 1. 启动 MySQL

确保 MySQL 运行在 `localhost:3306`，创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS ailll DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

执行建表 SQL：

```bash
# 在 ailll 数据库中执行
mysql -u root -p ailll < aiLLL/src/main/resources/sql/create_qa_history.sql
```

> `application-dev.yml` 默认使用用户 `root` 连接 `localhost:3306/ailll`，密码必须通过环境变量提供：
> ```bash
> set MYSQL_USERNAME=你的用户名
> set MYSQL_PASSWORD=你的密码
> ```

---

## 2. 启动 Redis

确保 Redis 运行在 `localhost:6379`。

如果使用 Docker：

```bash
docker run -d --name redis-dev -p 6379:6379 redis:7
```

> Redis 默认连接 `localhost:6379`，也可以通过 `REDIS_HOST` 和 `REDIS_PORT` 覆盖。

---

## 3. 启动 Python RAG 服务

```powershell
cd ragagent

# 首次运行：安装依赖
pip install -r requirements.txt

# 设置 DashScope API Key (阿里云百炼平台)
$env:DASHSCOPE_API_KEY = "你的APIKey"

# 启动服务 (端口 8001)
python -m uvicorn rag_server:app --host 0.0.0.0 --port 8001
```

验证：

```powershell
# 健康检查
curl http://localhost:8001/api/rag/health

# 语义检索测试
curl -X POST http://localhost:8001/api/rag/search -H "Content-Type: application/json" -d "{\"knowledgeBaseId\":\"agent\",\"query\":\"扫地机器人\",\"topK\":3}"
```

---

## 4. 启动 Java Spring Boot 后端

```powershell
cd aiLLL

# 必填：至少 32 字节的 JWT 签名密钥
$env:JWT_SECRET = "请替换为至少32字节的随机字符串"

# 按本机环境填写
$env:MYSQL_USERNAME = "root"
$env:MYSQL_PASSWORD = "你的MySQL密码"
$env:LLM_API_KEY = "你的DashScopeAPIKey"

# Windows 使用 mvnw.cmd
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

或使用 Bash：

```bash
cd aiLLL
bash ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

服务启动在 `http://localhost:8080`。

### dev profile 说明

`application-dev.yml` 做了以下覆盖：

| 配置项 | 覆盖值 | 原因 |
|--------|--------|------|
| `spring.datasource.*` | `localhost:3306` + 环境变量兜底 | 指向本地 MySQL |
| `spring.rabbitmq.host` | `localhost` | 避免连接超时 |
| `spring.rabbitmq.listener.simple.auto-startup` | `false` | 禁用 RabbitMQ Listener，纯同步测试不需要 MQ |
| `qa.rate-limit.max-per-minute` | `10` | 用户级限流阈值 |

### LLM API Key 配置

API Key 通过环境变量传入，不硬编码：

```powershell
$env:LLM_API_KEY = "你的DashScopeAPIKey"
$env:LLM_MODEL = "qwen-turbo"
```

也可以在启动命令中直接指定：

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev" -Dspring-boot.run.jvmArguments="-DLLM_API_KEY=xxx"
```

---

## 5. 验证完整调用链

### 5.1 注册用户

```powershell
curl -X POST http://localhost:8080/auth/register -H "Content-Type: application/json" -d "{\"username\":\"lsh\",\"password\":\"666\"}"
```

### 5.2 登录获取 JWT

```powershell
curl -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" -d "{\"username\":\"lsh\",\"password\":\"666\"}"
```

返回：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "userId": 1
  }
}
```

### 5.3 知识库问答

```powershell
curl -X POST http://localhost:8080/api/chat/ask -H "Content-Type: application/json" -H "Authorization: Bearer <token>" -d "{\"knowledgeBaseId\":\"agent\",\"sessionId\":\"test-001\",\"question\":\"扫地机器人怎么重置地图？\"}"
```

返回：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "sessionId": "test-001",
    "answer": "根据知识库资料，重置地图的方法是……",
    "references": [
      {"source": "扫地机器人100问2.txt", "chunkId": "a1b2c3d4", "score": 0.64}
    ]
  }
}
```

### 5.4 查看问答历史

```sql
SELECT id, session_id, status, LEFT(question, 50) AS q, LEFT(answer, 50) AS a, create_time
FROM qa_history
ORDER BY create_time DESC
LIMIT 5;
```

---

## 可选：异步问答测试

需要额外启动 RabbitMQ 并以 `dev,mq` profile 启动 Java：

```powershell
# 启动 RabbitMQ
docker run -d --name rabbitmq-dev -p 5672:5672 -p 15672:15672 -e RABBITMQ_DEFAULT_USER=guest -e RABBITMQ_DEFAULT_PASS=guest rabbitmq:3-management

# 以 dev,mq profile 启动 Java
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,mq"
```

异步接口：`POST /api/chat/ask-async`，先连接 WebSocket `ws://localhost:8080/ws/chat?userId=1` 接收推送。
