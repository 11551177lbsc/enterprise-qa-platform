package com.haust.ailll.service;

import com.haust.ailll.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private static final long LIMIT = 10L; // 每分钟限制次数

    @Autowired(required = false)
    private RedisUtil redisUtil;

    /**
     * 检查 userId 是否在允许范围内。返回 true 表示允许，false 表示被限流。
     */
    public boolean isAllowed(String userId) {
        if (userId == null || userId.isEmpty()) {
            userId = "anonymous";
        }
        long minuteBucket = System.currentTimeMillis() / 60000;
        String key = "rate:ai:" + userId + ":" + minuteBucket;
        Long count = null;
        if (redisUtil != null) {
            count = redisUtil.incr(key);
            // 如果是第一次创建，设置过期 1 分钟
            if (count != null && count == 1L) {
                // 使用 set 设置过期（单位为分钟）并保持值为 1
                redisUtil.set(key, "1", 1);
            }
        }
        // 在极端情况下 redisUtil 为空或返回 null，允许通过
        return count == null || count <= LIMIT;
    }//作用 ：检查用户是否在允许范围内

    public long getCurrentCount(String userId) {
        if (userId == null || userId.isEmpty()) {
            userId = "anonymous";
        }
        long minuteBucket = System.currentTimeMillis() / 60000;
        String key = "rate:ai:" + userId + ":" + minuteBucket;
        try {
            String v = redisUtil == null ? null : redisUtil.get(key);
            return v == null ? 0L : Long.parseLong(v);
        } catch (Exception e) {
            return 0L;
        }
    }//作用 ：获取当前用户在当前时间段内的调用次数
}

