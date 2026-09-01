# 评测基线

项目把“代码是否通过”和“知识效果是否可接受”分开评测。

## 确定性回归

```powershell
.\.venv\Scripts\python.exe -m pytest ragagent\tests -q
Set-Location aiLLL
.\mvnw.cmd test
```

覆盖工具路由、审批、拒绝零副作用、低置信升级、JWT、运行终态并发保护、失败审计、工单 SLA/时间线和知识缺口权限。

## 真实向量库评测

加载个人 `.env` 后运行：

```powershell
.\.venv\Scripts\python.exe -m ragagent.evals.run_retrieval_eval
```

脚本只读取黄金样例和本地 ChromaDB，不修改数据。输出每个问题的命中文档、首个相关文档排名，以及总体 Recall@5 和 MRR。

2026-09-02 在当前 326 个本地片段、4 条黄金样例上的结果：

| 指标 | 结果 |
| --- | --- |
| Recall@5 | 1.0000 |
| MRR | 0.8750 |

这个结果只能作为当前小样本回归基线，不能表述为生产准确率。扩充知识库或切换 Embedding 模型后必须重新运行。
