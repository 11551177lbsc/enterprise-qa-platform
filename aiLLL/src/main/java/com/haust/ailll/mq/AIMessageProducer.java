package com.haust.ailll.mq;

import com.haust.ailll.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AIMessageProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void sendAiRequest(String userId, String message) {
        String payload = (userId == null ? "anonymous" : userId) + "|" + (message == null ? "" : message);
        // 使用默认交换机，routingKey 为队列名
        rabbitTemplate.convertAndSend(RabbitMQConfig.AI_CHAT_QUEUE, payload);
    }
}//把消息发到指定队列

