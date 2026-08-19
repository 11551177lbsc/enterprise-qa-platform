# 本地启动指南

## 推荐：Docker Compose

1. 在项目根目录复制配置模板：

```powershell
Copy-Item .env.example .env
```

2. 修改 `.env`：

- `MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD`：两个本地数据库密码。
- `JWT_SECRET`：Java 与 Python 共用，至少 32 字节。
- `AGENT_SERVICE_TOKEN`：内部工具网关专用，至少 32 字节，不能与 JWT 密钥相同。
- `DASHSCOPE_API_KEY`、`LLM_API_KEY`：调用真实 Embedding/LLM 时填写。

3. 启动：

```powershell
docker compose up --build
```

4. 验证：

```powershell
Invoke-RestMethod http://127.0.0.1:8001/health
Invoke-RestMethod http://127.0.0.1:8001/api/rag/health
```

数据库表由 Flyway 自动管理。不要再手工执行 `CREATE TABLE`，也不要直接修改已执行的迁移文件；结构变化应新增下一个版本迁移。

## 不使用 Docker

### 1. 基础设施

准备 MySQL 8、Redis 7。首次只需创建空数据库：

```sql
CREATE DATABASE IF NOT EXISTS ailll
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

只需要你执行这一条的情况：本机没有 `ailll` 数据库，且你选择不用 Docker。建表仍由 Flyway 完成。

### 2. Java

```powershell
Set-Location aiLLL
$env:JWT_SECRET = "至少32字节随机字符串"
$env:AGENT_SERVICE_TOKEN = "另一个至少32字节随机字符串"
$env:MYSQL_URL = "jdbc:mysql://127.0.0.1:3306/ailll?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:MYSQL_USERNAME = "root"
$env:MYSQL_PASSWORD = "你的本机密码"
$env:REDIS_HOST = "127.0.0.1"
$env:LLM_API_KEY = "你的DashScopeKey"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

### 3. Python Agent

在另一个 PowerShell 窗口执行：

```powershell
Set-Location enterprise-qa-platform
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r ragagent\requirements.txt

$env:AGENT_ENV = "dev"
$env:AGENT_CHECKPOINT_BACKEND = "redis"
$env:REDIS_URL = "redis://127.0.0.1:6379/1"
$env:JAVA_TOOL_BASE_URL = "http://127.0.0.1:8080"
$env:JWT_SECRET = "与Java相同的JWT密钥"
$env:AGENT_SERVICE_TOKEN = "与Java相同的服务令牌"
$env:DASHSCOPE_API_KEY = "你的DashScopeKey"

.\.venv\Scripts\python.exe -m uvicorn ragagent.main:app --host 0.0.0.0 --port 8001
```

测试阶段可将 `AGENT_CHECKPOINT_BACKEND=memory`、`AGENT_PLANNER_MODE=heuristic`，此时不依赖 Redis 和模型服务；生产环境启动保护会拒绝这种配置。

### 4. 可选 Streamlit 演示

```powershell
.\.venv\Scripts\python.exe -m streamlit run ragagent\app.py
```

先通过 Java `/auth/login` 获取 JWT，再粘贴到侧边栏。

## 运行测试

```powershell
.\.venv\Scripts\python.exe -m pytest ragagent\tests -q
Set-Location aiLLL
.\mvnw.cmd test
```
