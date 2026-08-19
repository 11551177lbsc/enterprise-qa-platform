package com.haust.ailll.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionManager {

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void addSession(String userId, WebSocketSession session) {
        if (userId == null) return;
        sessions.put(userId, session);
    }//方法作用：添加session

    public void removeSession(String userId) {
        if (userId == null) return;
        sessions.remove(userId);
    }

    public WebSocketSession getSession(String userId) {
        return userId == null ? null : sessions.get(userId);
    }
}//方法作用：获取session

