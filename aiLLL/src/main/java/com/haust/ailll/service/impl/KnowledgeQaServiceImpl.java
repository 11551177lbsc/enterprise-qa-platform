package com.haust.ailll.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haust.ailll.ai.AiClient;
import com.haust.ailll.client.RagSearchClient;
import com.haust.ailll.dto.*;
import com.haust.ailll.entity.QaHistory;
import com.haust.ailll.mapper.QaHistoryMapper;
import com.haust.ailll.mq.QaTaskProducer;
import com.haust.ailll.service.KnowledgeQaService;
import com.haust.ailll.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识库问答服务实现
 *
 * 同步流程：Redis 限流 → RAG 检索 → 构造 Prompt → 调用 LLM → 持久化 → 返回
 * 异步流程：Redis 限流 → 创建 PENDING 记录 → 投递 MQ → 返回 taskId
 */
@Service
public class KnowledgeQaServiceImpl implements KnowledgeQaService {

    @Autowired
    private RagSearchClient ragSearchClient;

    @Autowired
    private AiClient aiClient;

    @Autowired
    private QaHistoryMapper qaHistoryMapper;

    @Autowired
    private QaTaskProducer qaTaskProducer;

    @Autowired(required = false)
    private RedisUtil redisUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int DEFAULT_TOP_K = 3;

    @Value("${qa.rate-limit.max-per-minute:10}")
    private int maxPerMinute;

    private static final String SYSTEM_PROMPT =
            "你是企业知识库智能问答助手。请仅基于以下提供的参考资料回答用户问题。" +
            "如果参考资料中没有相关信息，请如实告知用户：" +
            "\"抱歉，当前知识库中没有找到与您问题相关的信息。\"\n" +
            "回答时请引用参考资料的编号（如【参考资料1】），以便用户追溯信息来源。" +
            "回答应简洁、准确、专业。";

    // =====================================================================
    // 核心 QA 结果（供同步和消费者共用）
    // =====================================================================

    /**
     * RAG + LLM 核心流程的内部结果对象
     */
    public static class QaCoreResult {
        public String answer;
        public List<RagChunkDTO> chunks;
        public List<ReferenceDTO> references;
        public String status;
        public String errorMessage;
    }

    /**
     * 执行 RAG 检索 + LLM 回答的核心流程（供同步接口和异步 Consumer 共用）
     *
     * @param question        用户问题
     * @param knowledgeBaseId 知识库ID
     * @return QaCoreResult 包含 answer / references / chunks / status / errorMessage
     */
    public QaCoreResult executeQaCore(String question, String knowledgeBaseId) {
        QaCoreResult result = new QaCoreResult();
        result.chunks = new ArrayList<>();
        result.references = new ArrayList<>();

        // Step 1: RAG 检索
        RagSearchResponse ragResponse = ragSearchClient.search(knowledgeBaseId, question, DEFAULT_TOP_K);
        List<RagChunkDTO> chunks = ragResponse.getChunks();

        // 2a: RAG 不可用
        if (ragResponse.getError() != null && !ragResponse.getError().isEmpty()) {
            result.answer = "知识库检索服务暂时不可用，请稍后重试。";
            result.status = QaHistory.STATUS_FAILED;
            result.errorMessage = ragResponse.getError();
            return result;
        }

        // 2b: 无检索结果
        if (chunks == null || chunks.isEmpty()) {
            result.answer = "抱歉，当前知识库中没有找到与您问题相关的信息。";
            result.status = QaHistory.STATUS_NO_CONTEXT;
            result.chunks = new ArrayList<>();
            return result;
        }

        result.chunks = chunks;

        // Step 3: 构建 references
        List<ReferenceDTO> references = chunks.stream().map(chunk -> {
            ReferenceDTO ref = new ReferenceDTO();
            ref.setSource(chunk.getSource());
            ref.setChunkId(chunk.getChunkId());
            ref.setScore(chunk.getScore());
            return ref;
        }).collect(Collectors.toList());
        result.references = references;

        // Step 4: 拼接上下文
        StringBuilder ctx = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RagChunkDTO c = chunks.get(i);
            ctx.append(String.format("【参考资料%d】(来源: %s): %s\n", i + 1, c.getSource(), c.getContent()));
        }

        // Step 5: 调用 LLM
        String userMessage = String.format("参考资料：\n%s\n用户问题：%s", ctx.toString(), question);
        try {
            String answer = aiClient.chatWithSystem(SYSTEM_PROMPT, userMessage);
            if (answer == null || answer.isEmpty() || "AI解析失败".equals(answer)) {
                result.answer = "AI 服务返回异常，请稍后重试。";
                result.status = QaHistory.STATUS_FAILED;
                result.errorMessage = "LLM returned empty or parse error";
                result.references = new ArrayList<>();
            } else {
                result.answer = answer;
                result.status = QaHistory.STATUS_SUCCESS;
            }
        } catch (Exception e) {
            result.answer = "AI 服务调用失败，请稍后重试。";
            result.status = QaHistory.STATUS_FAILED;
            result.errorMessage = "LLM call exception: " + e.getMessage();
            result.references = new ArrayList<>();
        }

        return result;
    }

    // =====================================================================
    // 同步问答（现有接口，不变）
    // =====================================================================

    @Override
    public KnowledgeQaResponse ask(Long userId, String knowledgeBaseId, String sessionId, String question) {

        // Step 0: Redis 用户级限流
        if (!checkRateLimit(userId)) {
            KnowledgeQaResponse resp = new KnowledgeQaResponse();
            resp.setSessionId(sessionId);
            resp.setAnswer("请求过于频繁，请稍后再试（每分钟最多 " + maxPerMinute + " 次）。");
            resp.setReferences(new ArrayList<>());
            return resp;
        }

        // 执行核心流程
        QaCoreResult result = executeQaCore(question, knowledgeBaseId);

        // 持久化
        saveHistory(userId, sessionId, question, result.answer, result.references, result.status, result.errorMessage);

        // 返回
        KnowledgeQaResponse resp = new KnowledgeQaResponse();
        resp.setSessionId(sessionId);
        resp.setAnswer(result.answer);
        resp.setReferences(result.references);
        return resp;
    }

    // =====================================================================
    // 异步问答（新增）
    // =====================================================================

    @Override
    public QaAsyncResponse askAsync(Long userId, String knowledgeBaseId, String sessionId, String question) {

        // Step 0: Redis 用户级限流
        if (!checkRateLimit(userId)) {
            QaAsyncResponse resp = new QaAsyncResponse();
            resp.setTaskId(null);
            resp.setSessionId(sessionId);
            resp.setStatus("REJECTED");
            return resp;
        }

        // Step 1: 生成 taskId
        String taskId = UUID.randomUUID().toString();

        // Step 2: 创建 PENDING 记录
        QaHistory history = new QaHistory();
        history.setUserId(userId);
        history.setSessionId(sessionId != null ? sessionId : "");
        history.setQuestion(question);
        history.setStatus(QaHistory.STATUS_PENDING);
        qaHistoryMapper.insert(history);

        // Step 3: 投递 MQ
        QaTaskMessage taskMsg = new QaTaskMessage();
        taskMsg.setTaskId(taskId);
        taskMsg.setHistoryId(history.getId());
        taskMsg.setUserId(userId);
        taskMsg.setKnowledgeBaseId(knowledgeBaseId);
        taskMsg.setSessionId(sessionId);
        taskMsg.setQuestion(question);
        qaTaskProducer.sendTask(taskMsg);

        // Step 4: 立即返回
        QaAsyncResponse resp = new QaAsyncResponse();
        resp.setTaskId(taskId);
        resp.setSessionId(sessionId);
        resp.setStatus("PENDING");
        return resp;
    }

    // =====================================================================
    // Private Helpers
    // =====================================================================

    private boolean checkRateLimit(Long userId) {
        if (redisUtil == null) return true;
        try {
            String rateKey = (userId != null) ? "qa:rate:user:" + userId : "qa:rate:anonymous";
            long bucket = System.currentTimeMillis() / 60000;
            String key = rateKey + ":" + bucket;
            Long count = redisUtil.incr(key);
            if (count != null && count == 1L) {
                redisUtil.set(key, "1", 1);
            }
            return count == null || count <= maxPerMinute;
        } catch (Exception e) {
            return true;
        }
    }

    private void saveHistory(Long userId, String sessionId, String question,
                             String answer, List<ReferenceDTO> references,
                             String status, String errorMessage) {
        try {
            QaHistory h = new QaHistory();
            h.setUserId(userId);
            h.setSessionId(sessionId != null ? sessionId : "");
            h.setQuestion(question);
            h.setAnswer(answer);
            String refJson = "[]";
            if (references != null && !references.isEmpty()) {
                try { refJson = objectMapper.writeValueAsString(references); } catch (Exception ignored) {}
            }
            h.setReferencesJson(refJson);
            h.setStatus(status);
            h.setErrorMessage(errorMessage);
            qaHistoryMapper.insert(h);
        } catch (Exception e) {
            System.err.println("[KnowledgeQa] Failed to save qa_history: " + e.getMessage());
        }
    }
}
