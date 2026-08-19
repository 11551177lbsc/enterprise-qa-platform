from __future__ import annotations

import hashlib
from typing import Any


class KnowledgeSearchTool:
    def __init__(self, vector_store: Any | None = None):
        self._vector_store = vector_store

    def _store(self):
        if self._vector_store is None:
            from ragagent.rag.vector_store import VectorStoreService

            self._vector_store = VectorStoreService().vector_store
        return self._vector_store

    async def search(self, query: str, top_k: int = 5) -> list[dict[str, Any]]:
        results = await self._store().asimilarity_search_with_score(query, k=max(1, min(top_k, 20)))
        chunks: list[dict[str, Any]] = []
        for document, distance in results:
            content = document.page_content
            source = str(document.metadata.get("source", "unknown")).replace("\\", "/").split("/")[-1]
            chunks.append(
                {
                    "chunkId": hashlib.sha256(content.encode("utf-8")).hexdigest()[:16],
                    "content": content,
                    "source": source,
                    "score": round(1.0 / (1.0 + float(distance)), 4),
                }
            )
        return sorted(chunks, key=lambda item: item["score"], reverse=True)
