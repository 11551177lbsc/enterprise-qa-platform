package com.haust.ailll.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haust.ailll.dto.QaTaskMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.haust.ailll.config.RabbitMQConfig.QA_RAG_TASK_QUEUE;

/**
 * RAG 异步任务生产者 — 投递到独立队列 qa.rag.task.queue
 */
@Component
public class QaTaskProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void sendTask(QaTaskMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            rabbitTemplate.convertAndSend(QA_RAG_TASK_QUEUE, json);
            System.out.println("[QaTaskProducer] Task sent: " + message.getTaskId());
        } catch (Exception e) {
            System.err.println("[QaTaskProducer] Failed to send task: " + e.getMessage());
            throw new RuntimeException("Failed to send RAG task to MQ", e);
        }
    }
}
