package com.hmdp.utils;

import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    public static final long BEGIN_TIMESTAMP = 1640995200L;

    private static final int COUNT_BITS = 32;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        // Id由符号位(1位), 时间戳(31位), 序列号(32位)构成
        // 生成时间戳, 自某一天开始过了多少秒
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 生成序列号, 在这一秒中最多有多少订单增长
        // 序列号不能一直增长, 否则会溢出
        // 每天序列号都重新开始增长
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 拼接
        return timestamp << COUNT_BITS | count;
    }
}