package com.haust.ailll.config;

import com.haust.ailll.websocket.ChatWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private ChatWebSocketHandler chatWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat").setAllowedOrigins("*");
    }// 注册 WebSocket 端点
}

//把路径（例如 /ws/chat）注册到 ChatWebSocketHandler，+
// 接受客户端的 WS 握手请求。