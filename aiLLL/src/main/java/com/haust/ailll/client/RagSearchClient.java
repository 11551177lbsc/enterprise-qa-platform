package com.haust.ailll.client;

import com.haust.ailll.config.RagConfig;
import com.haust.ailll.dto.RagSearchRequest;
import com.haust.ailll.dto.RagSearchResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * RAG 检索 HTTP 客户端
 * 封装对 Python FastAPI RAG 服务的 HTTP 调用，不暴露 HTTP 细节给上层。
 */
@Component
public class RagSearchClient {

    @Autowired
    private RagConfig ragConfig;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 调用 Python RAG 服务的 /api/rag/search 接口进行语义检索
     *
     * @param knowledgeBaseId 知识库ID（对应 ChromaDB collection）
     * @param query           用户查询
     * @param topK            返回的 Top-K 数量
     * @return RagSearchResponse 包含检索结果或错误信息
     */
    public RagSearchResponse search(String knowledgeBaseId, String query, Integer topK) {
        String url = ragConfig.getBaseUrl() + "/api/rag/search";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        RagSearchRequest requestBody = new RagSearchRequest();
        requestBody.setKnowledgeBaseId(knowledgeBaseId);
        requestBody.setQuery(query);
        requestBody.setTopK(topK);

        HttpEntity<RagSearchRequest> request = new HttpEntity<>(requestBody, headers);

        try {
            return restTemplate.postForObject(url, request, RagSearchResponse.class);
        } catch (Exception e) {
            // Python 服务不可达或返回异常时，返回带 error 信息的响应
            RagSearchResponse errorResponse = new RagSearchResponse();
            errorResponse.setError("RAG service unavailable: " + e.getMessage());
            return errorResponse;
        }
    }
}
