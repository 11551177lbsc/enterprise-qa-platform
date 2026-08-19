package com.haust.ailll.config;

import com.haust.ailll.interceptor.JwtInterceptor;
import com.haust.ailll.interceptor.AgentServiceTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

import jakarta.annotation.Resource;
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private JwtInterceptor jwtInterceptor;

    @Resource
    private AgentServiceTokenInterceptor agentServiceTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/register",
                        "/login.html",
                        "/register.html",
                        "/chat.html",
                        "/**/*.html",
                        "/**/*.css",
                        "/**/*.js"
                );

        registry.addInterceptor(agentServiceTokenInterceptor)
                .addPathPatterns("/internal/agent-tools/**")
                .order(2);

    }

}
