package com.haust.ailll.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {

        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(factory);

        // key
        redisTemplate.setKeySerializer(new StringRedisSerializer());

        // value JSON序列化（新版推荐）
        redisTemplate.setValueSerializer(RedisSerializer.json());

        // hash
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(RedisSerializer.json());

        redisTemplate.afterPropertiesSet();

        return redisTemplate;
    }

}