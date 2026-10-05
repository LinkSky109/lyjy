package com.lhjypt.core.config;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版 {@link LockStore}，用于多实例并发/过期语义测试。
 */
public class InMemoryLockStore implements LockStore {

    private record Entry(String value, long expireAtMillis) {
    }

    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();

    @Override
    public synchronized boolean acquire(String key, String value, Duration ttl) {
        long now = System.currentTimeMillis();
        Entry current = map.get(key);
        if (current != null && current.expireAtMillis() > now) {
            return false;
        }
        map.put(key, new Entry(value, now + ttl.toMillis()));
        return true;
    }

    @Override
    public synchronized boolean release(String key, String value) {
        Entry current = map.get(key);
        if (current != null && current.value().equals(value)) {
            map.remove(key);
            return true;
        }
        return false;
    }
}
