# 独立部署基线

`deploy/` 提供个人项目的生产化模板，不包含任何公司服务器地址、证书或密钥。

拓扑：Nginx 网关 → Streamlit 支持中心 / Agent API / Java API。MySQL 和 Redis 只加入内部网络，不发布宿主机端口；Agent 和 Java 通过单独 egress 网络访问模型服务。

启动前需要：

- 在个人部署机准备 `.env`；
- 设置强随机 `JWT_SECRET` 和 `AGENT_SERVICE_TOKEN`；
- 设置 `PUBLIC_ORIGIN`、数据库密码和个人模型 Key；
- 完成 V3 数据库升级；
- 确认 ChromaDB 已完成受控入库或恢复。

```bash
cd deploy
docker compose config
docker compose build
docker compose up -d
docker compose ps
```

默认只把 Nginx 暴露到宿主机 `8088`。模板只提供 HTTP 内网入口；公网部署必须由负载均衡或受控网关终止 TLS，不能直接把该 HTTP 端口暴露到互联网。

当前模板尚未包含真实通知消费者、集中日志、云 Secret Manager 和数据库备份任务；这些需要在选定个人部署环境后绑定具体服务，不能仅靠占位配置声称已经生产就绪。
