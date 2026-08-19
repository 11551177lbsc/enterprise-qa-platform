package com.haust.ailll.mq;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haust.ailll.dto.QaTaskMessage;
import com.haust.ailll.dto.RagChunkDTO;
import com.haust.ailll.dto.ReferenceDTO;
import com.haust.ailll.entity.QaHistory;
import com.haust.ailll.mapper.QaHistoryMapper;
import com.haust.ailll.service.impl.KnowledgeQaServiceImpl;
import com.haust.ailll.websocket.QaWebSocketPushService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.haust.ailll.config.RabbitMQConfig.QA_RAG_TASK_QUEUE;

/**
 * RAG 异步任务消费者 — 监听 qa.rag.task.queue
 * 流程: 接收消息 → RAG检索 → LLM回答 → 更新DB → WebSocket推送
 */
@Component
public class QaTaskConsumer {

    @Autowired
    private KnowledgeQaServiceImpl knowledgeQaService;

    @Autowired
    private QaHistoryMapper qaHistoryMapper;

    @Autowired
    private QaWebSocketPushService pushService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @RabbitListener(queues = QA_RAG_TASK_QUEUE)
    public void handleRagTask(String payload) {
        QaTaskMessage task = null;
        try {
            // 1. 解析消息
            task = objectMapper.readValue(payload, QaTaskMessage.class);
            System.out.println("[QaTaskConsumer] Received task: " + task.getTaskId());

            // 2. 执行 RAG + LLM 核心流程（复用同步接口逻辑）
            KnowledgeQaServiceImpl.QaCoreResult result = knowledgeQaService.executeQaCore(
                    task.getQuestion(), task.getKnowledgeBaseId());

            // 3. 序列化 references 为 JSON
            String refJson = "[]";
            if (result.references != null && !result.references.isEmpty()) {
                try {
                    refJson = objectMapper.writeValueAsString(result.references);
                } catch (Exception ignored) {}
            }

            // 4. 更新 qa_history 记录
            QaHistory history = new QaHistory();
            history.setId(task.getHistoryId());
            history.setAnswer(result.answer);
            history.setReferencesJson(refJson);
            history.setStatus(result.status);
            history.setErrorMessage(result.errorMessage);
            qaHistoryMapper.updateStatus(history);

            // 5. WebSocket 推送
            List<Map<String, Object>> refMaps = buildRefMaps(result.references);
            pushService.pushQaResult(task.getUserId(), task.getTaskId(), task.getSessionId(),
                    result.status, result.answer, refMaps, result.errorMessage);

            System.out.println("[QaTaskConsumer] Task completed: " + task.getTaskId()
                    + " status=" + result.status);

        } catch (Exception e) {
            System.err.println("[QaTaskConsumer] Fatal error handling task: " + e.getMessage());
            e.printStackTrace();

            // 异常时尝试更新数据库状态
            if (task != null && task.getHistoryId() != null) {
                try {
                    QaHistory history = new QaHistory();
                    history.setId(task.getHistoryId());
                    history.setStatus(QaHistory.STATUS_FAILED);
                    history.setErrorMessage("Consumer error: " + e.getMessage());
                    qaHistoryMapper.updateStatus(history);
                } catch (Exception dbEx) {
                    System.err.println("[QaTaskConsumer] Failed to update error status: " + dbEx.getMessage());
                }
            }
            // WebSocket 推送失败信息
            if (task != null) {
                try {
                    pushService.pushQaResult(task.getUserId(), task.getTaskId(), task.getSessionId(),
                            QaHistory.STATUS_FAILED, null, null, "Consumer error: " + e.getMessage());
                } catch (Exception wsEx) {
                    System.err.println("[QaTaskConsumer] Failed to push error via WebSocket: " + wsEx.getMessage());
                }
            }
        }
    }

    private List<Map<String, Object>> buildRefMaps(List<ReferenceDTO> references) {
        if (references == null || references.isEmpty()) return new ArrayList<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ReferenceDTO ref : references) {
            Map<String, Object> m = new HashMap<>();
            m.put("source", ref.getSource());
            m.put("chunkId", ref.getChunkId());
            m.put("score", ref.getScore());
            result.add(m);
        }
        return result;
    }
}
