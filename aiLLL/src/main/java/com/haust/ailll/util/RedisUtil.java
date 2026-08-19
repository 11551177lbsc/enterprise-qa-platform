package com.haust.ailll.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedisUtil {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 存数据
    public void set(String key,String value,long time){

        stringRedisTemplate.opsForValue().set(key,value,time, TimeUnit.MINUTES);

    }

    // 取数据
    public String get(String key){

        return stringRedisTemplate.opsForValue().get(key);

    }

    // 删除
    public void delete(String key){

        stringRedisTemplate.delete(key);

    }

    // 自增
    public Long incr(String key){

        return stringRedisTemplate.opsForValue().increment(key);

    }

}