package com.haust.ailll.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class AgentServiceTokenInterceptor implements HandlerInterceptor {

    private final byte[] expectedToken;

    public AgentServiceTokenInterceptor(@Value("${agent.service-token}") String serviceToken) {
        this.expectedToken = serviceToken == null ? new byte[0] : serviceToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String supplied = request.getHeader("X-Agent-Service-Token");
        byte[] suppliedBytes = supplied == null ? new byte[0] : supplied.getBytes(StandardCharsets.UTF_8);
        if (expectedToken.length < 32 || !MessageDigest.isEqual(expectedToken, suppliedBytes)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Agent 服务凭据无效");
            return false;
        }
        return true;
    }
}
