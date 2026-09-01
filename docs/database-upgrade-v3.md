# V3 支持闭环数据库升级

本次升级只操作个人项目数据库。代码不会替你连接 DataGrip 执行 SQL。

## 执行前

1. 暂停本地 Java 服务，避免升级过程中写入工单。
2. 在 DataGrip 确认连接是个人电脑的 `127.0.0.1` 或 `localhost`。
3. 执行：

```sql
SELECT DATABASE() AS db_name, @@hostname AS mysql_host, CURRENT_USER() AS db_user;
```

`db_name` 必须是个人项目库，例如 `ailll_agent_test`。

## 执行升级

在 DataGrip 打开并执行这个文件的全部内容：

```text
aiLLL/src/main/resources/db/migration/V3__support_resolution_workflow.sql
```

脚本使用 `CREATE TABLE IF NOT EXISTS` 新增表，并以可重复执行的方式给历史工单补齐详情和初始时间线：

- `support_ticket_detail`：分类、型号、SLA、知识置信度、升级原因和分派信息；
- `support_ticket_event`：工单处理时间线；
- `answer_feedback`：答案是否解决问题；
- `knowledge_gap`：聚合后的高频未解决问题；
- `user_role`：支持中心角色。

## 验证

```sql
SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
    'support_ticket_detail',
    'support_ticket_event',
    'answer_feedback',
    'knowledge_gap',
    'user_role'
  )
ORDER BY TABLE_NAME;

SELECT COUNT(*) AS detail_count FROM support_ticket_detail;
SELECT COUNT(*) AS event_count FROM support_ticket_event;
SELECT COUNT(*) AS feedback_count FROM answer_feedback;
SELECT COUNT(*) AS gap_count FROM knowledge_gap;
```

第一条查询必须返回 5 行。

## 给个人账号授予知识治理权限（可选）

```sql
SELECT id, username FROM `user` ORDER BY id;
```

把 `YOUR_LOCAL_USERNAME` 换成自己的本地账号：

```sql
INSERT INTO user_role(user_id, role)
SELECT id, 'KNOWLEDGE_ADMIN'
FROM `user`
WHERE username = 'YOUR_LOCAL_USERNAME'
ON DUPLICATE KEY UPDATE role = VALUES(role), updated_at = NOW();
```

允许的角色为 `USER`、`SUPPORT_AGENT`、`KNOWLEDGE_ADMIN`、`ADMIN`。没有记录的账号按 `USER` 处理。

## 执行后

重新启动 Java。Flyway 会执行 V3；建表和历史回填均可重复执行，因此会安全跳过已有数据并登记迁移版本。不要手工修改 `flyway_schema_history`。
