package com.lhjypt.core.service.risk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenBlockRegistry 行为测试（P1 事中风控·限制开仓登记器）
 * 纯逻辑单元测试，通过可注入时钟验证冷却/过期语义。
 */
class OpenBlockRegistryTest {

    /** 测试专用可推进时钟（test-only 工具，不放生产类） */
    static class MutableClock implements LongSupplier {
        private final AtomicLong now = new AtomicLong(1_000_000L);
        @Override public long getAsLong() { return now.get(); }
        void advance(long ms) { now.addAndGet(ms); }
    }

    @Test
    @DisplayName("登记限制后，对应账户的新开仓被判定为受限")
    void blockedAfterRegister() {
        OpenBlockRegistry reg = new OpenBlockRegistry(new MutableClock());
        reg.block("ACCOUNT", "A1", 60);
        assertThat(reg.isBlocked("A1", null, null)).isTrue();
    }

    @Test
    @DisplayName("未登记的账户不受限")
    void unregisteredNotBlocked() {
        OpenBlockRegistry reg = new OpenBlockRegistry(new MutableClock());
        assertThat(reg.isBlocked("A1", null, null)).isFalse();
    }

    @Test
    @DisplayName("显式解除后恢复放行")
    void releasedAfterExplicitRelease() {
        OpenBlockRegistry reg = new OpenBlockRegistry(new MutableClock());
        reg.block("ACCOUNT", "A1", 60);
        reg.release("ACCOUNT", "A1");
        assertThat(reg.isBlocked("A1", null, null)).isFalse();
    }

    @Test
    @DisplayName("限制在有效时长内到期后自动放行")
    void blockAutoExpiresAfterDuration() {
        MutableClock clock = new MutableClock();
        OpenBlockRegistry reg = new OpenBlockRegistry(clock);
        reg.block("STRATEGY", "S1", 30);
        assertThat(reg.isBlocked(null, "S1", null)).isTrue();

        clock.advance(31_000); // 超过 30 秒
        assertThat(reg.isBlocked(null, "S1", null)).isFalse();
    }

    @Test
    @DisplayName("限制时长 <=0 视为永久，不自动过期，只能显式解除")
    void permanentBlockRequiresExplicitRelease() {
        MutableClock clock = new MutableClock();
        OpenBlockRegistry reg = new OpenBlockRegistry(clock);
        reg.block("ACCOUNT", "A1", 0);

        clock.advance(10_000_000L); // 推进很久
        assertThat(reg.isBlocked("A1", null, null)).isTrue();

        reg.release("ACCOUNT", "A1");
        assertThat(reg.isBlocked("A1", null, null)).isFalse();
    }

    @Test
    @DisplayName("标的维度限制命中该标的，不影响其它标的")
    void symbolScopeBlocksOnlyMatchingSymbol() {
        OpenBlockRegistry reg = new OpenBlockRegistry(new MutableClock());
        reg.block("SYMBOL", "rb2410", 60);
        assertThat(reg.isBlocked(null, null, "rb2410")).isTrue();
        assertThat(reg.isBlocked(null, null, "hc2410")).isFalse();
    }

    @Test
    @DisplayName("账户/策略/标的任一维度命中即视为受限")
    void anyDimensionHitBlocks() {
        OpenBlockRegistry reg = new OpenBlockRegistry(new MutableClock());
        reg.block("STRATEGY", "S1", 60);
        // 传入匹配的账户(未限制)、受限策略、任意标的 → 因策略命中而受限
        assertThat(reg.isBlocked("A1", "S1", "rb2410")).isTrue();
    }

    @Test
    @DisplayName("生效清单只包含未过期的限制项")
    void activeBlocksExcludesExpired() {
        MutableClock clock = new MutableClock();
        OpenBlockRegistry reg = new OpenBlockRegistry(clock);
        reg.block("ACCOUNT", "A1", 10);
        reg.block("ACCOUNT", "A2", 0); // 永久

        assertThat(reg.activeBlocks()).contains("ACCOUNT:A1", "ACCOUNT:A2");

        clock.advance(11_000); // A1 过期
        assertThat(reg.activeBlocks()).doesNotContain("ACCOUNT:A1")
                .contains("ACCOUNT:A2");
    }
}
