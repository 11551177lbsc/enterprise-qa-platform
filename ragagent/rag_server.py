"""
RAG REST API 服务
FastAPI 封装现有的 RAG 检索能力，提供 HTTP 接口供 Java 后端调用。
不负责用户系统、会话管理、权限控制 —— 这些由 Java 后端处理。
"""
import hashlib
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import List, Optional

from rag.vector_store import VectorStoreService
from utils.config_handler import chroma_conf, rag_conf
from utils.path_tool import get_abs_path


# =============================================================================
# Pydantic Models
# =============================================================================

class SearchRequest(BaseModel):
    knowledgeBaseId: str = Field(default="agent", description="知识库ID，对应 ChromaDB collection 名称")
    query: str = Field(..., description="用户搜索查询")
    topK: int = Field(default=5, ge=1, le=50, description="返回的 Top-K 结果数量")


class RagChunk(BaseModel):
    chunkId: str
    content: str
    score: float
    source: str


class SearchResponse(BaseModel):
    chunks: List[RagChunk]


class HealthResponse(BaseModel):
    status: str
    collection: str
    model: str


class IndexResponse(BaseModel):
    status: str
    message: str


# =============================================================================
# FastAPI App
# =============================================================================

app = FastAPI(title="Enterprise RAG Service", version="1.0.0")

# CORS — 允许 Java 后端跨域调用
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 全局服务实例（模块加载时初始化一次）
vector_store_service = VectorStoreService()


def _l2_to_similarity(distance: float) -> float:
    """
    将 ChromaDB 默认的 L2 distance 转换为语义正确的 similarity score。
    L2 distance 范围 [0, ∞)，0 = 完全相同。
    转换公式: similarity = 1 / (1 + distance)
    结果范围 (0, 1]，1.0 = 最相关，接近 0 = 不相关。
    """
    return round(1.0 / (1.0 + distance), 4)


def _generate_chunk_id(content: str) -> str:
    """基于内容生成唯一的 chunk ID"""
    return hashlib.md5(content.encode("utf-8")).hexdigest()[:16]


# =============================================================================
# Endpoints
# =============================================================================

@app.get("/api/rag/health", response_model=HealthResponse)
async def health():
    """健康检查：返回服务状态"""
    try:
        collection_count = vector_store_service.vector_store._collection.count()
        return HealthResponse(
            status="ok",
            collection=f"{chroma_conf['collection_name']} (docs: {collection_count})",
            model=rag_conf.get("chat_model_name", "unknown"),
        )
    except Exception as e:
        raise HTTPException(status_code=503, detail=f"Service unhealthy: {str(e)}")


@app.post("/api/rag/search", response_model=SearchResponse)
async def search(request: SearchRequest):
    """
    语义检索接口：根据用户查询从知识库中检索 Top-K 相关文档片段。
    仅做检索，不做总结 —— 总结由 Java 后端负责。
    """
    try:
        # 使用 similarity_search_with_score 获取带分数的检索结果
        results = vector_store_service.vector_store.similarity_search_with_score(
            request.query,
            k=request.topK,
        )

        chunks = []
        for doc, distance in results:
            source = doc.metadata.get("source", "unknown")
            # 从完整路径中提取文件名
            if "/" in source or "\\" in source:
                source = source.replace("\\", "/").split("/")[-1]

            chunk_id = _generate_chunk_id(doc.page_content)
            # 将 ChromaDB 的 L2 distance 转换为语义正确的 similarity score
            # similarity 范围 (0, 1]，1.0 = 完全匹配，接近 0 = 不相关
            similarity = _l2_to_similarity(float(distance))
            chunks.append(RagChunk(
                chunkId=chunk_id,
                content=doc.page_content,
                score=similarity,
                source=source,
            ))

        # 按相关性降序排列（最高分在前）
        chunks.sort(key=lambda c: c.score, reverse=True)

        return SearchResponse(chunks=chunks)

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Search failed: {str(e)}")


@app.post("/api/rag/index", response_model=IndexResponse)
async def index():
    """
    文档入库接口：扫描 data/ 目录，对新文件进行切分、向量化并存入 ChromaDB。
    基于 MD5 去重，已入库的文件不会重复处理。
    """
    try:
        vector_store_service.load_document()
        return IndexResponse(
            status="indexed",
            message="Documents processed successfully. New files indexed, existing files skipped.",
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Indexing failed: {str(e)}")


# =============================================================================
# Entry Point
# =============================================================================

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)
