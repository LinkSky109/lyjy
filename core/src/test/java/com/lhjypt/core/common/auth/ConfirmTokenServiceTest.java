package com.lhjypt.core.common.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ConfirmTokenService 二次确认令牌行为测试（P1）
 * 支撑 PRD §3.5：强平/再平衡/降级开关等敏感操作需一次性短时口令。
 */
class ConfirmTokenServiceTest {

    static class MutableClock implements LongSupplier {
        private final AtomicLong now = new AtomicLong(0L);
        @Override public long getAsLong() { return now.get(); }
        void advance(long ms) { now.addAndGet(ms); }
    }

    private ConfirmTokenService service(MutableClock clock) {
        return new ConfirmTokenService(clock, 30_000L);
    }

    @Test
    @DisplayName("签发的令牌在有效期内可校验通过")
    void issuedTokenIsValid() {
        ConfirmTokenService svc = service(new MutableClock());
        String token = svc.issue("RISK_OFFICER");

        assertThat(svc.validate(token)).isTrue();
    }

    @Test
    @DisplayName("令牌一次性使用：校验通过后立即失效")
    void tokenIsSingleUse() {
        ConfirmTokenService svc = service(new MutableClock());
        String token = svc.issue("RISK_OFFICER");

        assertThat(svc.validate(token)).isTrue();
        assertThat(svc.validate(token)).isFalse();
    }

    @Test
    @DisplayName("令牌超过有效期后校验失败")
    void tokenExpiresAfterTtl() {
        MutableClock clock = new MutableClock();
        ConfirmTokenService svc = service(clock);
        String token = svc.issue("RISK_OFFICER");

        clock.advance(31_000);

        assertThat(svc.validate(token)).isFalse();
    }

    @Test
    @DisplayName("未知令牌校验失败")
    void unknownTokenRejected() {
        assertThat(service(new MutableClock()).validate("not-a-token")).isFalse();
    }

    @Test
    @DisplayName("空令牌直接拒绝，不抛异常")
    void nullOrBlankTokenRejected() {
        ConfirmTokenService svc = service(new MutableClock());
        assertThat(svc.validate(null)).isFalse();
        assertThat(svc.validate("")).isFalse();
        assertThat(svc.validate("   ")).isFalse();
    }

    @Test
    @DisplayName("多次签发的令牌彼此独立可用")
    void tokensAreIndependent() {
        ConfirmTokenService svc = service(new MutableClock());
        String t1 = svc.issue("TRADER");
        String t2 = svc.issue("TRADER");

        assertThat(svc.validate(t1)).isTrue();
        assertThat(svc.validate(t2)).isTrue();
    }
}
