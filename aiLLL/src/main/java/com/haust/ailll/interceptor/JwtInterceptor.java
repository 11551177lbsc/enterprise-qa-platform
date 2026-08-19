package com.haust.ailll.interceptor;

import com.haust.ailll.util.JwtUtil;
import com.haust.ailll.util.RedisUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

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
        // 1 获取token
        String token = request.getHeader("Authorization");
        System.out.println("请求token：" + token);
        if(token == null || token.isEmpty()){

            response.setStatus(401);
            response.getWriter().write("未登录");

            return false;
        }
        token = token.replace("Bearer ", "");
        try{

            // 2 解析token
            Long userId = jwtUtil.parseToken(token);
            System.out.println("token解析userId：" + userId);
            // 3 Redis key
            String redisKey = "login:" + userId;
            System.out.println("redisKey：" + redisKey);
            // 4 查询Redis
            String redisToken = redisUtil.get(redisKey);
            System.out.println("redisKey：" + redisKey);
            if(redisToken == null){

                response.setStatus(401);
                response.getWriter().write("登录过期");

                return false;
            }

            return true;

        }catch (Exception e){

            response.setStatus(401);
            response.getWriter().write("token无效");

            return false;
        }
    }
}