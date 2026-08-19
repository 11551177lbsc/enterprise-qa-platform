package com.haust.ailll.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haust.ailll.config.AiConfig;
import com.haust.ailll.config.LlmConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
public class AiClient {

    @Autowired
    private AiConfig aiConfig;

    @Autowired
    private LlmConfig llmConfig;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 简单对话（原有方法，保持向后兼容）
     * 用于 /chat/stream 流式对话
     */
    public String chat(String context) {

        String url = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + aiConfig.getApiKey());

        Map<String, Object> body = new HashMap<>();
        body.put("model", "qwen-turbo");

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", context);
        messages.add(userMessage);
        body.put("messages", messages);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(response.getBody());
            return node.get("choices").get(0).get("message").get("content").asText();
        } catch (Exception e) {
            e.printStackTrace();
            return "AI解析失败";
        }
    }

    /**
     * 带 System Prompt 的对话（新增方法）
     * 用于知识库问答流程 — KnowledgeQaService
     *
     * @param systemMessage 系统提示词（设定角色和规则）
     * @param userMessage   用户消息（包含知识库上下文 + 用户问题）
     * @return LLM 返回的答案文本
     */
    public String chatWithSystem(String systemMessage, String userMessage) {
        String url = llmConfig.getBaseUrl() + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + llmConfig.getApiKey());

        Map<String, Object> body = new HashMap<>();
        body.put("model", llmConfig.getModel());

        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> sysMsg = new HashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemMessage);
        messages.add(sysMsg);

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        body.put("messages", messages);

        System.out.println("========== RAG QA LLM 调用 ==========");
        System.out.println("Model: " + llmConfig.getModel());
        System.out.println("URL: " + url);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(response.getBody());
            return node.get("choices").get(0).get("message").get("content").asText();
        } catch (Exception e) {
            e.printStackTrace();
            return "AI解析失败";
        }
    }
}
