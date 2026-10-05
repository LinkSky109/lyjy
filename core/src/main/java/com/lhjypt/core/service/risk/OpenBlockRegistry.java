package com.lhjypt.core.service.risk;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 限制开仓登记器（P1）
 * 事中风控触发 BLOCK_OPEN 处置时登记被限制的作用域（账户/策略/标的），
 * 事前风控在下单校验时读取，命中则拦截新开仓。
 * 以内存实现 + 冷却自动过期，避免引入分布式依赖（对齐 P0 频率控制的进程内做法）。
 */
@Component
public class OpenBlockRegistry {

    /** scopeKey(类型:ID) -> 到期时间戳(ms) */
    private final Map<String, Long> blocks = new ConcurrentHashMap<>();

    /** 时间源，默认系统时钟；测试可注入可控时钟验证过期语义 */
    private final LongSupplier clock;

    public OpenBlockRegistry() {
        this(System::currentTimeMillis);
    }

    public OpenBlockRegistry(LongSupplier clock) {
        this.clock = clock;
    }

    private static String key(String scopeType, String scopeId) {
        return scopeType + ":" + scopeId;
    }

    /** 登记限制开仓，durationSec 秒后自动解除；<=0 表示需显式解除（永久） */
    public void block(String scopeType, String scopeId, int durationSec) {
        long expireAt = durationSec > 0 ? clock.getAsLong() + durationSec * 1000L : Long.MAX_VALUE;
        blocks.put(key(scopeType, scopeId), expireAt);
    }

    /** 显式解除限制 */
    public void release(String scopeType, String scopeId) {
        blocks.remove(key(scopeType, scopeId));
    }

    /** 是否被限制开仓（账户/策略/标的任一命中即视为限制） */
    public boolean isBlocked(String accountId, String strategyId, String symbol) {
        return active(key("ACCOUNT", accountId))
                || active(key("STRATEGY", strategyId))
                || active(key("SYMBOL", symbol));
    }

    private boolean active(String k) {
        Long expireAt = blocks.get(k);
        if (expireAt == null) return false;
        if (expireAt == Long.MAX_VALUE) return true;
        if (expireAt < clock.getAsLong()) {
            blocks.remove(k);
            return false;
        }
        return true;
    }

    /** 当前生效的限制清单（key=类型:ID） */
    public Set<String> activeBlocks() {
        long now = clock.getAsLong();
        blocks.entrySet().removeIf(e -> e.getValue() != Long.MAX_VALUE && e.getValue() < now);
        return Set.copyOf(blocks.keySet());
    }
}
