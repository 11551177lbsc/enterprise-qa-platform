package com.haust.ailll.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 异步问答 WebSocket 推送服务
 * 推送 JSON 消息到指定用户的 WebSocket 连接
 */
@Service
public class QaWebSocketPushService {

    @Autowired
    private WebSocketSessionManager sessionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 按 userId 推送 JSON 消息到 WebSocket
     *
     * @return true 推送成功，false 推送失败（无连接或发送异常）
     */
    public boolean pushToUser(Long userId, Map<String, Object> payload) {
        if (userId == null) {
            System.err.println("[QaPush] Cannot push: userId is null");
            return false;
        }
        try {
            WebSocketSession session = sessionManager.getSession(String.valueOf(userId));
            if (session != null && session.isOpen()) {
                String json = objectMapper.writeValueAsString(payload);
                session.sendMessage(new TextMessage(json));
                return true;
            } else {
                System.err.println("[QaPush] No open WebSocket session for userId=" + userId);
                return false;
            }
        } catch (Exception e) {
            System.err.println("[QaPush] Push failed for userId=" + userId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * 构造并推送 RAG 异步问答结果
     */
    public void pushQaResult(Long userId, String taskId, String sessionId,
                              String status, String answer,
                              List<Map<String, Object>> references, String errorMessage) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "qa_result");
        payload.put("taskId", taskId);
        payload.put("sessionId", sessionId);
        payload.put("status", status);
        payload.put("answer", answer);
        payload.put("references", references != null ? references : List.of());
        payload.put("errorMessage", errorMessage);

        pushToUser(userId, payload);
    }
}
