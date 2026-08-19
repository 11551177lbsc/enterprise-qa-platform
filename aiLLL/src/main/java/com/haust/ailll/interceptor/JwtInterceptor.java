package com.haust.ailll.interceptor;

import com.haust.ailll.util.JwtUtil;
import com.haust.ailll.util.RedisUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private RedisUtil redisUtil;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {

            response.setStatus(401);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("未登录");

            return false;
        }
        String token = authorization.substring(7).trim();
        try{

            // 2 解析token
            Long userId = jwtUtil.parseToken(token);
            String redisKey = "login:" + userId;
            String redisToken = redisUtil.get(redisKey);
            if (redisToken == null || !MessageDigest.isEqual(
                    token.getBytes(StandardCharsets.UTF_8),
                    redisToken.getBytes(StandardCharsets.UTF_8))) {

                response.setStatus(401);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("登录过期");

                return false;
            }

            request.setAttribute("authenticatedUserId", userId);
            return true;

        }catch (Exception e){

            response.setStatus(401);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("token无效");

            return false;
        }
    }
}
