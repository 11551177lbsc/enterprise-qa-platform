package com.haust.ailll.dto;

import lombok.Data;

/**
 * RAG 异步任务消息 — 投递到 qa.rag.task.queue 的消息体
 */
@Data
public class QaTaskMessage {
    private String taskId;
    private Long historyId;
    private Long userId;
    private String knowledgeBaseId;
    private String sessionId;
    private String question;
}
