package com.haust.ailll.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 配置 — 绑定 application.yml 中 llm.* 属性
 * 用于知识库问答流程（KnowledgeQaService），与旧的 AiConfig（ai.*）分离
 */
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmConfig {

    private String apiKey;

    private String baseUrl;

    private String model;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
