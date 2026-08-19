package com.haust.ailll.service.impl;

import com.haust.ailll.ai.AiClient;
import com.haust.ailll.entity.ChatMessage;
import com.haust.ailll.mapper.ChatMapper;
import com.haust.ailll.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ChatServiceImpl implements ChatService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private AiClient aiClient;

    @Autowired
    private ChatMapper chatMapper;

    private static final String CONTEXT_KEY_PREFIX = "chat:context:";

    @Override
    public String chat(Long userId, String question) {

        // ================== Redis限流 ==================
        String rateKey = "rate:" + userId;
        Long count = stringRedisTemplate.opsForValue().increment(rateKey);
        if (count == 1) {
            stringRedisTemplate.expire(rateKey, 60, TimeUnit.SECONDS);
        }
        if (count > 10) {
            return "请求过于频繁，请稍后再试";
        }

        // ================== Redis聊天上下文Key ==================
        String contextKey = CONTEXT_KEY_PREFIX + userId;

        // ================== 读取Redis聊天历史 ==================
        List<String> history = stringRedisTemplate.opsForList().range(contextKey, 0, -1);

        StringBuilder prompt = new StringBuilder();

        if (history != null) {
            for (String msg : history) {
                prompt.append(msg).append("\n");
            }
        }

        // 当前用户问题
        prompt.append("用户：").append(question);

        // ================== 调用AI ==================
        String answer = aiClient.chat(prompt.toString());

        // ================== 保存上下文到Redis ==================
        stringRedisTemplate.opsForList().rightPush(contextKey, "用户：" + question);
        stringRedisTemplate.opsForList().rightPush(contextKey, "AI：" + answer);

        // 保留最近20条记录
        stringRedisTemplate.opsForList().trim(contextKey, -20, -1);

        // ================== 保存到数据库 ==================
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setUserId(userId);
        chatMessage.setQuestion(question);
        chatMessage.setAnswer(answer);
        chatMessage.setCreateTime(LocalDateTime.now());
        chatMapper.insert(chatMessage);

        return answer;
    }
}