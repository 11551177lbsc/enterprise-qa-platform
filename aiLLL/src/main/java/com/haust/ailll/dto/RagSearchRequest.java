package com.haust.ailll.dto;

import lombok.Data;

/**
 * 向 Python RAG 服务发起检索请求
 */
@Data
public class RagSearchRequest {
    private String knowledgeBaseId;
    private String query;
    private Integer topK;
}
