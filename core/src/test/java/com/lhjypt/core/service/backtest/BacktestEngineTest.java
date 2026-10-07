package com.lhjypt.core.service.backtest;

import com.lhjypt.core.model.market.TickData;
import com.lhjypt.core.service.backtest.impl.TickMatchingEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BacktestEngineTest {

    private final BacktestEngine engine = new BacktestEngine(
            mock(TickMatchingEngine.class),
            mock(PerformanceCalculator.class),
            mock(LookaheadBiasDetector.class));

    @Test
    @DisplayName("模拟行情：小数位受控，防止 BigDecimal scale 膨胀导致 BigInteger.pow 卡死")
    void generateMockTicksKeepsScaleBounded() {
        long startMs = System.currentTimeMillis();
        List<TickData> ticks = engine.generateMockTicks("BTCUSDT", 10000, new BigDecimal("3500"));
        long elapsedMs = System.currentTimeMillis() - startMs;

        assertThat(ticks).hasSize(10000);
        // 修复前：scale 随随机游走累积膨胀，10000 次后运算退化为超大整数乘法（卡死）；
        // 修复后：生成价格小数位恒 ≤8
        for (TickData tick : ticks) {
            assertThat(tick.getLastPrice().scale()).isLessThanOrEqualTo(8);
            assertThat(tick.getHigh().scale()).isLessThanOrEqualTo(8);
            assertThat(tick.getTurnover().scale()).isLessThanOrEqualTo(8);
        }
        // 性能守卫：10000 条应在秒级完成（修复前 >25s 甚至超时）
        assertThat(elapsedMs).isLessThan(10_000);
    }
}