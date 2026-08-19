package com.haust.ailll.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@Configuration
@EnableRabbit
public class RabbitMQConfig {

    // ---- 旧队列：普通 AI 聊天（保留不变）----
    public static final String AI_CHAT_QUEUE = "ai.chat.queue";

    // ---- 新队列：RAG 异步问答（新增）----
    public static final String QA_RAG_TASK_QUEUE = "qa.rag.task.queue";

    @Bean
    public Queue aiChatQueue() {
        return new Queue(AI_CHAT_QUEUE, true);
    }

    @Bean
    public Queue qaRagTaskQueue() {
        return new Queue(QA_RAG_TASK_QUEUE, true);
    }
}

