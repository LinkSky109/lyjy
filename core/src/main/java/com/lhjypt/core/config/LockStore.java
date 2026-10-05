package com.lhjypt.core.config;

import java.time.Duration;

/**
 * 分布式锁存储 SPI（默认 Redis 实现 {@link RedisLockStore}）。
 *
 * <p>抽象出来便于以内存实现做多实例并发正确性测试，也便于替换为其他后端（如 etcd）。
 */
public interface LockStore {

    /**
     * 以 NX + TTL 抢占锁。
     *
     * @param key   完整锁键（含前缀）
     * @param value 持有者标识
     * @param ttl   自动过期时间
     * @return true=抢到
     */
    boolean acquire(String key, String value, Duration ttl);

    /**
     * 释放锁：仅当当前持有者等于 value 时删除。
     *
     * @return true=已删除；false=不属于本持有者或不存在
     */
    boolean release(String key, String value);
}
