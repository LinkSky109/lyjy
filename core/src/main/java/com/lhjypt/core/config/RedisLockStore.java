package com.lhjypt.core.config;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 Redis 的 {@link LockStore} 实现：{@code SET key value NX PX ttl} 抢占，
 * 释放时校验持有者后删除（TTL 兜底）。
 */
@Component
@RequiredArgsConstructor
public class RedisLockStore implements LockStore {

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean acquire(String key, String value, Duration ttl) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(key, value, ttl));
    }

    @Override
    public boolean release(String key, String value) {
        String current = redisTemplate.opsForValue().get(key);
        if (value.equals(current)) {
            redisTemplate.delete(key);
            return true;
        }
        return false;
    }
}
