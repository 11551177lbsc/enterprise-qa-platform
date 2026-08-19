package com.haust.ailll.service;

import com.haust.ailll.util.RedisUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChatMemoryService {

    @Autowired(required = false)
    private RedisUtil redisUtil;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final int MAX_MESSAGES = 10;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public void saveMessage(String userId, String message) {
        if (userId == null || userId.isEmpty()) {
            userId = "anonymous";
        }
        String key = "chat:" + userId;
        if (redisUtil != null) {
            try {
                String json = redisUtil.get(key);
                List<String> list;
                if (json == null || json.isEmpty()) {
                    list = new ArrayList<>();
                } else {
                    list = objectMapper.readValue(json, new TypeReference<List<String>>() {});
                }
                list.add(message);
                if (list.size() > MAX_MESSAGES) {
                    list = list.subList(list.size() - MAX_MESSAGES, list.size());
                }
                // 存储为 JSON，过期时间设为 60 分钟
                redisUtil.set(key, objectMapper.writeValueAsString(list), 60);
                return;
            } catch (Exception e) {
                // 回退到 StringRedisTemplate
                e.printStackTrace();
            }
        }
        // fallback 使用 list ops
        stringRedisTemplate.opsForList().rightPush(key, message);
        stringRedisTemplate.opsForList().trim(key, -MAX_MESSAGES, -1);
    }//方法作用： 保存用户消息内存

    public List<String> getChatHistory(String userId) {
        if (userId == null || userId.isEmpty()) {
            userId = "anonymous";
        }
        String key = "chat:" + userId;
        if (redisUtil != null) {
            try {
                String json = redisUtil.get(key);
                if (json == null || json.isEmpty()) {
                    return new ArrayList<>();
                }
                return objectMapper.readValue(json, new TypeReference<List<String>>() {});
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        List<String> range = stringRedisTemplate.opsForList().range(key, 0, -1);
        return range == null ? new ArrayList<>() : range;
    }//方法作用： 获取用户消息

}
