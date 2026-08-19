package com.haust.ailll.dto;

import lombok.Data;

/**
 * 异步问答立即返回体 — POST /api/chat/ask-async
 */
@Data
public class QaAsyncResponse {
    private String taskId;
    private String sessionId;
    private String status;
}
