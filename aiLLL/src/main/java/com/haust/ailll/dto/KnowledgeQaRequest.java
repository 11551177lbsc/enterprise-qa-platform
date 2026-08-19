package com.haust.ailll.dto;

import lombok.Data;

/**
 * 知识库问答请求 — POST /api/chat/ask
 */
@Data
public class KnowledgeQaRequest {
    private String knowledgeBaseId;
    private String sessionId;
    private String question;
}
