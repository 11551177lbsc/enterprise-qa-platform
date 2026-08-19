from __future__ import annotations

import hashlib
import hmac

from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel, Field

router = APIRouter(prefix="/api/rag", tags=["rag"])


class SearchRequest(BaseModel):
    knowledgeBaseId: str = "agent"
    query: str = Field(min_length=1, max_length=4000)
    topK: int = Field(default=5, ge=1, le=50)


class RagChunk(BaseModel):
    chunkId: str
    content: str
    score: float
    source: str


def vector_service(request: Request):
    service = getattr(request.app.state, "vector_service", None)
    if service is None:
        from ragagent.rag.vector_store import VectorStoreService

        service = VectorStoreService()
        request.app.state.vector_service = service
    return service


@router.get("/health")
async def health(request: Request):
    try:
        service = vector_service(request)
        count = service.vector_store._collection.count()
        return {"status": "ok", "collection": f"agent (docs: {count})"}
    except Exception as exc:
        raise HTTPException(status_code=503, detail="RAG 向量库不可用") from exc


@router.post("/search")
async def search(payload: SearchRequest, request: Request):
    try:
        service = vector_service(request)
        results = await service.vector_store.asimilarity_search_with_score(payload.query, k=payload.topK)
        chunks = []
        for document, distance in results:
            content = document.page_content
            source = str(document.metadata.get("source", "unknown")).replace("\\", "/").split("/")[-1]
            chunks.append(
                RagChunk(
                    chunkId=hashlib.sha256(content.encode("utf-8")).hexdigest()[:16],
                    content=content,
                    score=round(1.0 / (1.0 + float(distance)), 4),
                    source=source,
                )
            )
        chunks.sort(key=lambda item: item.score, reverse=True)
        return {"chunks": chunks}
    except Exception as exc:
        raise HTTPException(status_code=500, detail="知识库检索失败") from exc


@router.post("/index")
async def index_documents(request: Request):
    if not request.app.state.settings.allow_indexing:
        raise HTTPException(status_code=403, detail="文档入库接口默认关闭，请显式设置 RAG_ALLOW_INDEXING=true")
    supplied = request.headers.get("X-Agent-Service-Token", "")
    expected = request.app.state.settings.agent_service_token
    if len(expected.encode("utf-8")) < 32 or not hmac.compare_digest(supplied, expected):
        raise HTTPException(status_code=403, detail="文档入库凭据无效")
    try:
        vector_service(request).load_document()
        return {"status": "indexed"}
    except Exception as exc:
        raise HTTPException(status_code=500, detail="文档入库失败") from exc
