package com.lhjypt.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 多实例并发语义：以内存锁存储模拟多实例争抢，验证任意时刻最多一个实例进入临界区；
 * 以及 TTL 到期后锁自动可被重新获取。
 */
class DistributedLockConcurrencyTest {

    @Test
    @DisplayName("多实例并发调度时临界区互斥（任意时刻最多一个执行）")
    void concurrentExecutionsAreMutuallyExclusive() throws Exception {
        InMemoryLockStore store = new InMemoryLockStore();
        ScheduleLockProperties properties = new ScheduleLockProperties();
        properties.setEnabled(true);
        properties.setTtlSeconds(60);
        DistributedLock lock = new DistributedLock(store, properties);

        int instances = 24;
        AtomicInteger inCritical = new AtomicInteger();
        AtomicInteger maxInCritical = new AtomicInteger();
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(instances);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < instances; i++) {
            Callable<Void> job = () -> {
                start.await();
                lock.execute("risk.intraday.evaluate", () -> {
                    int current = inCritical.incrementAndGet();
                    maxInCritical.accumulateAndGet(current, Math::max);
                    runs.incrementAndGet();
                    try {
                        Thread.sleep(15);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    inCritical.decrementAndGet();
                });
                return null;
            };
            futures.add(pool.submit(job));
        }

        start.countDown();
        for (Future<?> future : futures) {
            future.get(15, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(maxInCritical.get()).isEqualTo(1);
        assertThat(runs.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("TTL 到期后锁可被重新获取（实例崩溃兜底）")
    void expiredLockCanBeReacquired() throws Exception {
        InMemoryLockStore store = new InMemoryLockStore();

        assertThat(store.acquire("lhjypt:lock:x", "instance-1", Duration.ofMillis(60))).isTrue();
        assertThat(store.acquire("lhjypt:lock:x", "instance-2", Duration.ofMillis(60))).isFalse();

        Thread.sleep(120);
        assertThat(store.acquire("lhjypt:lock:x", "instance-2", Duration.ofMillis(60))).isTrue();
    }
}
