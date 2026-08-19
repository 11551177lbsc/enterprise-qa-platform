package com.haust.ailll.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 服务配置 — 绑定 application.yml 中 rag.* 属性
 * 指向 Python FastAPI RAG 服务的地址
 */
@Component
@ConfigurationProperties(prefix = "rag")
public class RagConfig {

    private String baseUrl;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
