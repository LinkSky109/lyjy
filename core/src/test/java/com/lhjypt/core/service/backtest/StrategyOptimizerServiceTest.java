package com.lhjypt.core.service.backtest;

import com.lhjypt.core.model.backtest.OptimizeRequest;
import com.lhjypt.core.model.backtest.OptimizeResult;
import com.lhjypt.core.model.market.BarData;
import com.lhjypt.core.service.backtest.impl.TickMatchingEngine;
import com.lhjypt.core.service.backtest.impl.TransactionCostCalculator;
import com.lhjypt.core.service.market.MarketDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StrategyOptimizerServiceTest {

    private MarketDataRepository repository;
    private StrategyOptimizerService service;

    @BeforeEach
    void setUp() {
        BacktestEngine engine = new BacktestEngine(
                new TickMatchingEngine(new TransactionCostCalculator()),
                new PerformanceCalculator(),
                new LookaheadBiasDetector());
        repository = mock(MarketDataRepository.class);
        service = new StrategyOptimizerService(engine, repository);
    }

    private static List<BarData> bars(int count) {
        List<BarData> bars = new ArrayList<>();
        // 构造带趋势+波动的 K 线序列，确保参数组合出现分化
        double price = 30000;
        for (int i = 0; i < count; i++) {
            price = price * (1 + (i % 13 == 0 ? 0.01 : 0.001) * (Math.sin(i / 4.0)) + 0.0005);
            BarData bar = new BarData();
            bar.setSymbol("BTC_USDT");
            bar.setFrequency("1h");
            bar.setOpen(BigDecimal.valueOf(price));
            bar.setHigh(BigDecimal.valueOf(price * 1.001));
            bar.setLow(BigDecimal.valueOf(price * 0.999));
            bar.setClose(BigDecimal.valueOf(price));
            bar.setVolume((i % 100) + 1);
            bar.setBarTime(LocalDateTime.now().plusHours(i));
            bars.add(bar);
        }
        return bars;
    }

    @Test
    @DisplayName("网格寻优：返回全部组合、按目标降序、最优存在、基准对照")
    void optimizeRunsGridSortedWithBaseline() {
        when(repository.queryBars(eq("BTC_USDT"), eq("1h"), any(), any(), eq(50000)))
                .thenReturn(bars(120));

        OptimizeRequest request = new OptimizeRequest();
        request.setSymbol("BTC_USDT");
        request.setFrequency("1h");
        request.setFastOptions(List.of(3, 5));
        request.setSlowOptions(List.of(15, 20));
        request.setObjective(OptimizeRequest.OBJ_SHARPE);
        request.setMaxDrawdownLimit(0.5);

        OptimizeResult result = service.optimize(request);

        assertThat(result.getBarsUsed()).isEqualTo(120);
        assertThat(result.getRuns()).hasSize(4); // 2x2
        assertThat(result.getBest()).isNotNull();
        // 按 objective（sharpe）降序
        for (int i = 1; i < result.getRuns().size(); i++) {
            assertThat(result.getRuns().get(i - 1).getSharpe())
                    .isGreaterThanOrEqualTo(result.getRuns().get(i).getSharpe());
        }
        assertThat(result.getBaseline()).isNotNull();
        assertThat(result.getBaseline().getFast()).isEqualTo(5);
        assertThat(result.getBaseline().getSlow()).isEqualTo(20);
    }

    @Test
    @DisplayName("回撤上限过滤生效")
    void drawdownLimitFiltersRuns() {
        when(repository.queryBars(eq("BTC_USDT"), eq("1h"), any(), any(), eq(50000)))
                .thenReturn(bars(120));

        OptimizeRequest request = new OptimizeRequest();
        request.setFastOptions(List.of(3, 5));
        request.setSlowOptions(List.of(15, 20));
        request.setMaxDrawdownLimit(0.0001); // 极严 → 大概率全过滤

        OptimizeResult result = service.optimize(request);

        assertThat(result.getRuns()).isEmpty();
        assertThat(result.getFilteredByDrawdown()).isGreaterThan(0);
    }

    @Test
    @DisplayName("slow<=fast 组合指标记 0 不参与最优")
    void invalidParameterGridIsTolerated() {
        when(repository.queryBars(eq("BTC_USDT"), eq("1h"), any(), any(), eq(50000)))
                .thenReturn(bars(120));

        OptimizeRequest request = new OptimizeRequest();
        request.setFastOptions(List.of(30));
        request.setSlowOptions(List.of(20, 30)); // 30/20 非法、30/30 非法
        request.setMaxDrawdownLimit(1.0);

        OptimizeResult result = service.optimize(request);

        assertThat(result.getRuns()).isEmpty(); // 非法组合不参与
        assertThat(result.getBest()).isNull();
    }
}