package com.haust.ailll.websocket;

import com.haust.ailll.mq.AIMessageProducer;
import com.haust.ailll.service.ChatMemoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.List;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private ChatMemoryService chatMemoryService;

    @Autowired
    private AIMessageProducer aiMessageProducer;

    @Autowired
    private WebSocketSessionManager sessionManager;

    private String extractUserId(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri == null) return null;
            String query = uri.getQuery();
            if (query == null) return null;
            for (String part : query.split("&")) {
                if (part.startsWith("userId=")) {
                    return part.substring("userId=".length());
                }
            }
        } catch (Exception ignored) {}
        Object uid = session.getAttributes().get("userId");
        return uid == null ? null : String.valueOf(uid);
    }//方法作用： 获取用户ID

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = extractUserId(session);
        if (userId == null || userId.isEmpty()) {
            userId = "anonymous";
        }
        sessionManager.addSession(userId, session);
        //握手成功后将 WebSocketSession 注册到 WebSocketSessionManager+
        // （保存 userId -> session 的映射），可做身份校验与历史回放。
        List<String> history = chatMemoryService.getChatHistory(userId);
        if (history != null && !history.isEmpty()) {
            for (String h : history) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(h));
                }
            }
        }
    }//方法作用：创建一个WebSocket会话


    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
        String userId = extractUserId(session);
        if (userId == null || userId.isEmpty()) {
            userId = "anonymous";
        }
        sessionManager.removeSession(userId);
    }//方法作用：处理文本消息

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String userId = extractUserId(session);
        if (userId == null || userId.isEmpty()) {
            userId = "anonymous";
        }
        String payload = message.getPayload();//获取消息体
        chatMemoryService.saveMessage(userId, "user: " + payload);
       //将用户消息写入内存/缓存
        aiMessageProducer.sendAiRequest(userId, payload);
        //推送到消息队列
        if (session.isOpen()) {
            session.sendMessage(new TextMessage("received"));
        }
    }//方法作用： 处理文本消息 每行解释 ： 1. 获取用户ID 2. 创建一个WebSocket会话 3. 处理文本消息 4. 保存消息 5. 发送请求给AI 6. 发送消息给用户
}

