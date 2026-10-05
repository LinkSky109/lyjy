package com.lhjypt.core.common.auth;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 敏感操作二次确认令牌服务（P1）
 * 支撑 PRD §3.5：强制平仓、再平衡执行、降级开关切换等敏感操作，
 * 必须先签发一次性短时令牌（默认30秒），操作时校验并立即消费。
 */
@Component
public class ConfirmTokenService {

    private final Map<String, Long> tokens = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private final long ttlMillis;
    private final SecureRandom random = new SecureRandom();

    public ConfirmTokenService() {
        this(System::currentTimeMillis, 30_000L);
    }

    public ConfirmTokenService(LongSupplier clock, long ttlMillis) {
        this.clock = clock;
        this.ttlMillis = ttlMillis;
    }

    /**
     * 为指定角色签发一次性确认令牌
     */
    public String issue(String role) {
        byte[] buf = new byte[24];
        random.nextBytes(buf);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        tokens.put(token, clock.getAsLong() + ttlMillis);
        return token;
    }

    /**
     * 校验并消费令牌：有效则一次性作废；过期/未知/空均拒绝
     */
    public boolean validate(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Long expireAt = tokens.get(token);
        if (expireAt == null) {
            return false;
        }
        // 无论是否过期都移除，防止重放
        tokens.remove(token);
        return clock.getAsLong() <= expireAt;
    }
}
