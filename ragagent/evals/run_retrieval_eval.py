"""在真实本地向量库上计算可复现的 Recall@K 与 MRR，不修改任何数据。"""

from __future__ import annotations

import asyncio
import json
from pathlib import Path

from ragagent.agent.tools.knowledge import KnowledgeSearchTool


async def main() -> None:
    cases_path = Path(__file__).with_name("retrieval_cases.json")
    cases = json.loads(cases_path.read_text(encoding="utf-8"))
    tool = KnowledgeSearchTool()
    recalls: list[float] = []
    reciprocal_ranks: list[float] = []

    for case in cases:
        results = await tool.search(case["question"], top_k=5)
        sources = [item["source"] for item in results]
        expected = set(case["expected_sources"])
        first_rank = next((index for index, source in enumerate(sources, 1) if source in expected), None)
        recalls.append(1.0 if first_rank else 0.0)
        reciprocal_ranks.append(1.0 / first_rank if first_rank else 0.0)
        print(json.dumps({
            "question": case["question"],
            "sources": sources,
            "hit": bool(first_rank),
            "firstRelevantRank": first_rank,
        }, ensure_ascii=False))

    count = len(cases) or 1
    print(json.dumps({
        "caseCount": len(cases),
        "recallAt5": round(sum(recalls) / count, 4),
        "mrr": round(sum(reciprocal_ranks) / count, 4),
    }, ensure_ascii=False))


if __name__ == "__main__":
    asyncio.run(main())
