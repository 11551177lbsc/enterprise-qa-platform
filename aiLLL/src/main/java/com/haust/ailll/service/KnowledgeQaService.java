package com.haust.ailll.service;

import com.haust.ailll.dto.KnowledgeQaResponse;
import com.haust.ailll.dto.QaAsyncResponse;

/**
 * 知识库问答服务接口
 */
public interface KnowledgeQaService {

    /**
     * 同步知识库问答
     *
     * @param userId          用户ID（从JWT解析，可能为null）
     * @param knowledgeBaseId 知识库ID
     * @param sessionId       会话ID
     * @param question        用户问题
     * @return 包含答案和引用来源的响应
     */
    KnowledgeQaResponse ask(Long userId, String knowledgeBaseId, String sessionId, String question);

    /**
     * 异步知识库问答
     * 创建 PENDING 记录 → 投递到 RabbitMQ → 立即返回 taskId
     *
     * @param userId          用户ID（从JWT解析，可能为null）
     * @param knowledgeBaseId 知识库ID
     * @param sessionId       会话ID
     * @param question        用户问题
     * @return 包含 taskId、sessionId、status=PENDING
     */
    QaAsyncResponse askAsync(Long userId, String knowledgeBaseId, String sessionId, String question);
}
