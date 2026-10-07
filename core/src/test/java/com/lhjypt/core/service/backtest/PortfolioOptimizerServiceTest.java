package com.lhjypt.core.service.backtest;

import com.lhjypt.core.model.backtest.PortfolioOptimizeRequest;
import com.lhjypt.core.model.backtest.PortfolioOptimizeResult;
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

class PortfolioOptimizerServiceTest {

    private MarketDataRepository repository;
    private PortfolioOptimizerService service;

    @BeforeEach
    void setUp() {
        BacktestEngine engine = new BacktestEngine(
                new TickMatchingEngine(new TransactionCostCalculator()),
                new PerformanceCalculator(),
                new LookaheadBiasDetector());
        repository = mock(MarketDataRepository.class);
        service = new PortfolioOptimizerService(engine, repository);
    }

    private static List<BarData> bars(String symbol, int count, double base) {
        List<BarData> bars = new ArrayList<>();
        double price = base;
        for (int i = 0; i < count; i++) {
            price = price * (1 + 0.002 * Math.sin(i / 5.0 + base % 7) + 0.0004);
            BarData bar = new BarData();
            bar.setSymbol(symbol);
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

    private static PortfolioOptimizeRequest request(String method) {
        PortfolioOptimizeRequest request = new PortfolioOptimizeRequest();
        PortfolioOptimizeRequest.Member a = new PortfolioOptimizeRequest.Member();
        a.setSymbol("BTC_USDT");
        a.setFast(5);
        a.setSlow(20);
        PortfolioOptimizeRequest.Member b = new PortfolioOptimizeRequest.Member();
        b.setSymbol("ETH_USDT");
        b.setFast(10);
        b.setSlow(30);
        request.setStrategies(List.of(a, b));
        request.setMethod(method);
        return request;
    }

    @Test
    @DisplayName("等权：权重 0.5/0.5，输出组合指标与等权对照")
    void equalWeight() {
        when(repository.queryBars(eq("BTC_USDT"), eq("1h"), any(), any(), eq(50000)))
                .thenReturn(bars("BTC_USDT", 150, 30000));
        when(repository.queryBars(eq("ETH_USDT"), eq("1h"), any(), any(), eq(50000)))
                .thenReturn(bars("ETH_USDT", 150, 2000));

        PortfolioOptimizeResult result = service.optimize(
                request(PortfolioOptimizeRequest.METHOD_EQUAL));

        assertThat(result.getMembers()).hasSize(2);
        assertThat(result.getMembers().get(0).getWeight()).isEqualTo(0.5);
        assertThat(result.getMembers().get(1).getWeight()).isEqualTo(0.5);
        assertThat(result.getBars()).isEqualTo(149);
        assertThat(result.getEqualWeightSharpe())
                .isEqualTo(result.getPortfolioSharpe()); // 等权即基准
    }

    @Test
    @DisplayName("风险平价与最大夏普：权重和为 1 且非负；最大夏普不劣于等权")
    void riskParityAndMaxSharpe() {
        when(repository.queryBars(eq("BTC_USDT"), eq("1h"), any(), any(), eq(50000)))
                .thenReturn(bars("BTC_USDT", 150, 30000));
        when(repository.queryBars(eq("ETH_USDT"), eq("1h"), any(), any(), eq(50000)))
                .thenReturn(bars("ETH_USDT", 150, 2000));

        PortfolioOptimizeResult rp = service.optimize(
                request(PortfolioOptimizeRequest.METHOD_RISK_PARITY));
        double rpSum = rp.getMembers().stream()
                .mapToDouble(PortfolioOptimizeResult.MemberResult::getWeight).sum();
        assertThat(rpSum).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(rp.getMembers()).allSatisfy(m -> assertThat(m.getWeight()).isGreaterThan(0.0));

        PortfolioOptimizeResult ms = service.optimize(
                request(PortfolioOptimizeRequest.METHOD_MAX_SHARPE));
        double msSum = ms.getMembers().stream()
                .mapToDouble(PortfolioOptimizeResult.MemberResult::getWeight).sum();
        assertThat(msSum).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(ms.getPortfolioSharpe())
                .isGreaterThanOrEqualTo(ms.getEqualWeightSharpe() - 1e-6);
    }

    @Test
    @DisplayName("无成员/超上限拒绝")
    void rejectsInvalidRequests() {
        PortfolioOptimizeRequest empty = new PortfolioOptimizeRequest();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.optimize(empty))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("成员");

        PortfolioOptimizeRequest tooMany = request("EQUAL");
        tooMany.setStrategies(List.of(
                new PortfolioOptimizeRequest.Member(), new PortfolioOptimizeRequest.Member(),
                new PortfolioOptimizeRequest.Member(), new PortfolioOptimizeRequest.Member(),
                new PortfolioOptimizeRequest.Member()));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.optimize(tooMany))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("上限");
    }
}