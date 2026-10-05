package com.lhjypt.core.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * DistributedLock 语义测试：禁用直通 / 抢锁执行 / 未抢到跳过 / Redis 故障 fail-open。
 * 全部基于 mock，无需真实 Redis。
 */
class DistributedLockTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private ScheduleLockProperties properties;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        properties = new ScheduleLockProperties();
        properties.setEnabled(true);
        properties.setTtlSeconds(60);
    }

    @Test
    @DisplayName("enabled=false 时直接执行，不访问 Redis")
    void disabledExecutesDirectlyWithoutRedis() {
        properties.setEnabled(false);
        DistributedLock lock = new DistributedLock(redisTemplate, properties);
        AtomicInteger runs = new AtomicInteger();

        boolean result = lock.execute("task.a", runs::incrementAndGet);

        assertThat(result).isTrue();
        assertThat(runs.get()).isEqualTo(1);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("抢到锁时执行任务并返回 true")
    void acquiredLockRunsTask() {
        when(valueOps.setIfAbsent(eq("lhjypt:lock:task.b"), anyString(), any(Duration.class)))
                .thenReturn(true);
        DistributedLock lock = new DistributedLock(redisTemplate, properties);
        AtomicInteger runs = new AtomicInteger();

        boolean result = lock.execute("task.b", runs::incrementAndGet);

        assertThat(result).isTrue();
        assertThat(runs.get()).isEqualTo(1);
        verify(valueOps).setIfAbsent(eq("lhjypt:lock:task.b"), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("未抢到锁时跳过执行并返回 false")
    void notAcquiredSkipsTask() {
        when(valueOps.setIfAbsent(eq("lhjypt:lock:task.c"), anyString(), any(Duration.class)))
                .thenReturn(false);
        DistributedLock lock = new DistributedLock(redisTemplate, properties);
        AtomicInteger runs = new AtomicInteger();

        boolean result = lock.execute("task.c", runs::incrementAndGet);

        assertThat(result).isFalse();
        assertThat(runs.get()).isZero();
    }

    @Test
    @DisplayName("Redis 抛异常时 fail-open，照常执行并返回 true")
    void redisExceptionFailsOpen() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("redis connection refused"));
        DistributedLock lock = new DistributedLock(redisTemplate, properties);
        AtomicInteger runs = new AtomicInteger();

        boolean result = lock.execute("task.d", runs::incrementAndGet);

        assertThat(result).isTrue();
        assertThat(runs.get()).isEqualTo(1);
    }
}
