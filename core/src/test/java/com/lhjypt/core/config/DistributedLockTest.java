package com.lhjypt.core.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * DistributedLock 语义测试：禁用直通 / 抢锁执行 / 未抢到跳过 / 锁存储故障 fail-open。
 */
class DistributedLockTest {

    private LockStore lockStore;
    private ScheduleLockProperties properties;

    @BeforeEach
    void setUp() {
        lockStore = mock(LockStore.class);
        properties = new ScheduleLockProperties();
        properties.setEnabled(true);
        properties.setTtlSeconds(60);
    }

    @Test
    @DisplayName("enabled=false 时直接执行，不访问锁存储")
    void disabledExecutesDirectlyWithoutStore() {
        properties.setEnabled(false);
        DistributedLock lock = new DistributedLock(lockStore, properties);
        AtomicInteger runs = new AtomicInteger();

        boolean result = lock.execute("task.a", runs::incrementAndGet);

        assertThat(result).isTrue();
        assertThat(runs.get()).isEqualTo(1);
        verifyNoInteractions(lockStore);
    }

    @Test
    @DisplayName("抢到锁时执行任务并释放锁")
    void acquiredLockRunsTaskAndReleases() {
        when(lockStore.acquire(eq("lhjypt:lock:task.b"), anyString(), any(Duration.class)))
                .thenReturn(true);
        DistributedLock lock = new DistributedLock(lockStore, properties);
        AtomicInteger runs = new AtomicInteger();

        boolean result = lock.execute("task.b", runs::incrementAndGet);

        assertThat(result).isTrue();
        assertThat(runs.get()).isEqualTo(1);
        verify(lockStore).release(eq("lhjypt:lock:task.b"), anyString());
    }

    @Test
    @DisplayName("未抢到锁时跳过执行且不释放")
    void notAcquiredSkipsTask() {
        when(lockStore.acquire(eq("lhjypt:lock:task.c"), anyString(), any(Duration.class)))
                .thenReturn(false);
        DistributedLock lock = new DistributedLock(lockStore, properties);
        AtomicInteger runs = new AtomicInteger();

        boolean result = lock.execute("task.c", runs::incrementAndGet);

        assertThat(result).isFalse();
        assertThat(runs.get()).isZero();
        verify(lockStore, never()).release(anyString(), anyString());
    }

    @Test
    @DisplayName("锁存储抛异常时 fail-open，照常执行并返回 true")
    void storeExceptionFailsOpen() {
        when(lockStore.acquire(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("redis connection refused"));
        DistributedLock lock = new DistributedLock(lockStore, properties);
        AtomicInteger runs = new AtomicInteger();

        boolean result = lock.execute("task.d", runs::incrementAndGet);

        assertThat(result).isTrue();
        assertThat(runs.get()).isEqualTo(1);
    }
}
