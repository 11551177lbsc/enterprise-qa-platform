package com.haust.ailll.service;

import com.haust.ailll.dto.agenttool.AnswerFeedbackRequest;
import com.haust.ailll.mapper.SupportWorkflowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class SupportWorkflowService {

    private static final Set<String> KNOWLEDGE_ROLES = Set.of("SUPPORT_AGENT", "KNOWLEDGE_ADMIN", "ADMIN");
    private final SupportWorkflowMapper mapper;

    public SupportWorkflowService(SupportWorkflowMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> recordFeedback(Long userId, AnswerFeedbackRequest request) {
        String previous = mapper.findFeedbackOutcome(request.runId(), userId);
        mapper.upsertFeedback(
                userId,
                request.runId(),
                request.question().trim(),
                request.outcome(),
                blankToNull(request.comment())
        );

        boolean knowledgeGapRecorded = false;
        if ("UNRESOLVED".equals(request.outcome()) && !"UNRESOLVED".equals(previous)) {
            mapper.upsertKnowledgeGap(
                    fingerprint(request.question()),
                    request.question().trim(),
                    request.confidence(),
                    request.runId()
            );
            knowledgeGapRecorded = true;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recorded", true);
        result.put("outcome", request.outcome());
        result.put("knowledgeGapRecorded", knowledgeGapRecorded);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> currentRole(Long userId) {
        String role = mapper.findRole(userId);
        return Map.of("role", role == null ? "USER" : role);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listKnowledgeGaps(Long userId, int limit) {
        requireKnowledgeRole(userId);
        return mapper.findOpenKnowledgeGaps(Math.max(1, Math.min(limit, 100)));
    }

    public Map<String, Object> updateKnowledgeGapStatus(Long userId, Long gapId, String status) {
        requireKnowledgeRole(userId);
        if (mapper.updateKnowledgeGapStatus(gapId, status) != 1) {
            throw new IllegalArgumentException("知识缺口不存在");
        }
        return Map.of("gapId", gapId, "status", status);
    }

    private void requireKnowledgeRole(Long userId) {
        String role = mapper.findRole(userId);
        if (role == null || !KNOWLEDGE_ROLES.contains(role)) {
            throw new SecurityException("当前账号无权管理知识缺口");
        }
    }

    private static String fingerprint(String question) {
        try {
            String normalized = question.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("问题指纹生成失败", ex);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
