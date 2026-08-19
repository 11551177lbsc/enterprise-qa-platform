package com.haust.ailll.mq;

import com.haust.ailll.config.RabbitMQConfig;
import com.haust.ailll.service.ChatMemoryService;
import com.haust.ailll.websocket.WebSocketSessionManager;
import com.haust.ailll.ai.AiClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class AIMessageConsumer {

    @Autowired
    private ChatMemoryService chatMemoryService;

    @Autowired
    private WebSocketSessionManager sessionManager;

    @Autowired
    private AiClient aiClient;

    @RabbitListener(queues = RabbitMQConfig.AI_CHAT_QUEUE)
    public void handleAiRequest(String payload) {
        try {
            String userId = "anonymous";
            String message = payload;
            if (payload != null && payload.contains("|")) {
                String[] parts = payload.split("\\|", 2);
                userId = parts[0];
                message = parts.length > 1 ? parts[1] : "";
            }

            // 构造上下文：历史对话 + 当前用户消息
            StringBuilder contextBuilder = new StringBuilder();
            try {
                var history = chatMemoryService.getChatHistory(userId);
                if (history != null && !history.isEmpty()) {
                    for (String h : history) {
                        contextBuilder.append(h).append("\n");
                    }
                }
            } catch (Exception ignored) {
            }
            contextBuilder.append("user: ").append(message);
            String context = contextBuilder.toString();

            // 调用真实 AI 客户端
            String aiReply = aiClient.chat(context);
            if (aiReply == null) {
                aiReply = "AI返回为空";
            }

            // 保存回复到聊天记忆
            chatMemoryService.saveMessage(userId, "assistant: " + aiReply);

            // 通过 websocket 发送回去
            WebSocketSession session = sessionManager.getSession(userId);
            if (session != null && session.isOpen()) {
                session.sendMessage(new TextMessage(aiReply));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
