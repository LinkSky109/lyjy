package com.lhjypt.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Duration;
import java.util.UUID;

/**
 * 轻量级分布式调度锁（PRD §10.1/§10.2）。
 *
 * <p>语义：
 * <ul>
 *   <li>{@code enabled=false}：直接执行任务，返回 true（本地/单实例）。</li>
 *   <li>{@code enabled=true}：以 {@code lhjypt:lock:<key>} 为键执行 {@code SET NX PX}，
 *       抢到锁才执行任务，执行完释放；未抢到返回 false（其他实例正在执行）。</li>
 *   <li>Redis 不可用（连接异常/超时等）：记录 warn 并照常执行（fail-open 降级），返回 true，
 *       避免因监控组件故障导致交易调度停摆。</li>
 * </ul>
 *
 * <p>锁的 TTL 是实例崩溃时的兜底释放；正常路径在执行结束后主动释放。释放为 best-effort，
 * 仅当锁值仍为当前实例标识时才删除，避免误删其他实例的锁。</p>
 */
@Component
public class DistributedLock {

    private static final Logger log = LoggerFactory.getLogger(DistributedLock.class);
    private static final String KEY_PREFIX = "lhjypt:lock:";

    private final StringRedisTemplate redisTemplate;
    private final ScheduleLockProperties properties;
    private final String instanceId;

    public DistributedLock(StringRedisTemplate redisTemplate, ScheduleLockProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.instanceId = resolveInstanceId();
    }

    /**
     * 尝试获取分布式锁并执行任务。
     *
     * @param key  业务键（会加 {@code lhjypt:lock:} 前缀）
     * @param task 抢到锁后执行的任务
     * @return true=已执行（抢到锁或降级执行）；false=未抢到锁，未执行
     */
    public boolean execute(String key, Runnable task) {
        if (!properties.isEnabled()) {
            task.run();
            return true;
        }

        String redisKey = KEY_PREFIX + key;
        boolean acquired;
        try {
            Boolean result = redisTemplate.opsForValue()
                    .setIfAbsent(redisKey, instanceId, Duration.ofSeconds(properties.getTtlSeconds()));
            acquired = Boolean.TRUE.equals(result);
        } catch (Exception e) {
            // fail-open：Redis 故障时降级为照常执行，保证调度不中断
            log.warn("分布式锁获取异常，降级为照常执行: key={}, error={}", key, e.getMessage());
            task.run();
            return true;
        }

        if (!acquired) {
            log.debug("未获得分布式锁，跳过本次调度: key={}", key);
            return false;
        }

        try {
            task.run();
        } finally {
            releaseQuietly(redisKey);
        }
        return true;
    }

    private void releaseQuietly(String redisKey) {
        try {
            String current = redisTemplate.opsForValue().get(redisKey);
            if (instanceId.equals(current)) {
                redisTemplate.delete(redisKey);
            }
        } catch (Exception e) {
            // 释放失败无碍：TTL 到期会自动释放
            log.warn("分布式锁释放异常（将由 TTL 自动过期）: key={}, error={}", redisKey, e.getMessage());
        }
    }

    private String resolveInstanceId() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            String pid = ManagementFactory.getRuntimeMXBean().getName();
            return host + ":" + pid;
        } catch (Exception e) {
            return "instance-" + UUID.randomUUID();
        }
    }
}
