package com.haust.ailll.interceptor;

import com.haust.ailll.service.RateLimitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    @Autowired
    private RateLimitService rateLimitService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isEmpty()) {
            userId = request.getParameter("userId");
        }
        boolean allowed = rateLimitService.isAllowed(userId);
        if (!allowed) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Too Many Requests\"}");
            return false;
        }
        return true;
    }
}

