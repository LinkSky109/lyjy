package com.lhjypt.core.service.backtest;

import com.lhjypt.core.mapper.StrategyInfoMapper;
import com.lhjypt.core.mapper.StrategyOptimizationMapper;
import com.lhjypt.core.model.backtest.RobustnessRequest;
import com.lhjypt.core.model.backtest.RobustnessResult;
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

class StrategyOptimizerRobustnessTest {

    private MarketDataRepository repository;
    private StrategyOptimizerService service;

    @BeforeEach
    void setUp() {
        BacktestEngine engine = new BacktestEngine(
                new TickMatchingEngine(new TransactionCostCalculator()),
                new PerformanceCalculator(),
                new LookaheadBiasDetector());
        repository = mock(MarketDataRepository.class);
        service = new StrategyOptimizerService(engine, repository,
                mock(StrategyOptimizationMapper.class), mock(StrategyInfoMapper.class));
    }

    private static List<BarData> bars(int count) {
        List<BarData> bars = new ArrayList<>();
        double price = 30000;
        for (int i = 0; i < count; i++) {
            price = price * (1 + 0.003 * Math.sin(i / 6.0) + 0.0003);
            BarData bar = new BarData();
            bar.setSymbol("BTC_USDT");
            bar.setFrequency("1h");
            bar.setOpen(BigDecimal.valueOf(price));
            bar.setHigh(BigDecimal.valueOf(price * 1.001));
            bar.setLow(BigDecimal.valueOf(price * 0.999));
            bar.setClose(BigDecimal.valueOf(price));
            bar.setVolume((i % 50) + 1);
            bar.setBarTime(LocalDateTime.now().plusHours(i));
            bars.add(bar);
        }
        return bars;
    }

    private static RobustnessRequest request() {
        RobustnessRequest request = new RobustnessRequest();
        request.setSymbol("BTC_USDT");
        request.setTrainRatio(0.7);
        request.setFolds(4);
        request.setFastOptions(List.of(3, 5));
        request.setSlowOptions(List.of(15, 20));
        request.setMaxDrawdownLimit(1.0);
        return request;
    }

    @Test
    @DisplayName("稳健性：样本内/外切分、退化率、多折与结论")
    void robustnessProducesOosAndFolds() {
        when(repository.queryBars(eq("BTC_USDT"), eq("1h"), any(), any(), eq(50000)))
                .thenReturn(bars(300));

        RobustnessResult result = service.robustness(request()).get(0);

        assertThat(result.getBars()).isEqualTo(300);
        assertThat(result.getTrainBars()).isEqualTo(210);
        assertThat(result.getTestBars()).isEqualTo(90);
        assertThat(result.getTrainBest()).isNotNull();
        assertThat(result.getOosBest()).isNotNull();
        assertThat(result.getFolds()).hasSize(4);
        assertThat(result.getParamStability()).isBetween(0.0, 1.0);
        assertThat(result.getVerdict()).isIn("ROBUST", "OVERFIT_RISK");
    }

    @Test
    @DisplayName("样本不足 → INSUFFICIENT_DATA")
    void insufficientData() {
        when(repository.queryBars(eq("BTC_USDT"), eq("1h"), any(), any(), eq(50000)))
                .thenReturn(bars(60)); // train 42 < 50

        RobustnessResult result = service.robustness(request()).get(0);

        assertThat(result.getVerdict()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(result.getTrainBest()).isNull();
    }
}