package com.lhjypt.core.service.attribution;

import com.lhjypt.core.mapper.AttributionReportMapper;
import com.lhjypt.core.model.attribution.BrinsonLine;
import com.lhjypt.core.model.attribution.BrinsonRequest;
import com.lhjypt.core.model.attribution.ExecutionRequest;
import com.lhjypt.core.model.attribution.FactorRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AttributionServiceTest {

    private AttributionService service;

    @BeforeEach
    void setUp() {
        service = new AttributionService(mock(AttributionReportMapper.class));
    }

    @Test
    @DisplayName("Brinson：分配/选股/交互分解且恒等式成立 rP-rB=Σ效应")
    void brinsonIdentityHolds() {
        BrinsonRequest request = new BrinsonRequest();
        request.setLines(List.of(
                line("G1", "0.60", "0.10", "0.50", "0.08"),
                line("G2", "0.40", "0.02", "0.50", "0.04")));

        Map<String, Object> result = service.brinson(request);

        assertThat((double) result.get("portfolioReturn")).isCloseTo(0.068, within(1e-9));
        assertThat((double) result.get("benchmarkReturn")).isCloseTo(0.06, within(1e-9));
        assertThat((double) result.get("excessReturn")).isCloseTo(0.008, within(1e-9));
        assertThat(result.get("identityCheck")).isEqualTo(Boolean.TRUE);
        assertThat((List<?>) result.get("groups")).hasSize(2);
    }

    @Test
    @DisplayName("因子暴露：按因子聚合 权重×暴露度")
    void factorExposureAggregates() {
        FactorRequest request = new FactorRequest();
        request.setWeights(Map.of("A", new BigDecimal("0.7"), "B", new BigDecimal("0.3")));

        Map<String, Map<String, BigDecimal>> exposures = new LinkedHashMap<>();
        exposures.put("A", Map.of("size", BigDecimal.ONE));
        exposures.put("B", Map.of("size", new BigDecimal("-1"), "value", new BigDecimal("0.5")));
        request.setExposures(exposures);

        Map<String, Object> result = service.factor(request);

        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> factorExposures =
                (Map<String, BigDecimal>) result.get("factorExposures");
        assertThat(factorExposures.get("size").doubleValue()).isCloseTo(0.4, within(1e-9));
        assertThat(factorExposures.get("value").doubleValue()).isCloseTo(0.15, within(1e-9));
        assertThat((double) result.get("totalWeight")).isCloseTo(1.0, within(1e-9));
    }

    @Test
    @DisplayName("执行归因：IS 与滑点（bps）")
    void executionShortfallComputesBps() {
        ExecutionRequest request = new ExecutionRequest();
        request.setSide("BUY");
        request.setDecisionPrice(new BigDecimal("100"));
        request.setArrivalPrice(new BigDecimal("101"));
        request.setFillPrice(new BigDecimal("102"));
        request.setFillQuantity(new BigDecimal("10"));

        Map<String, Object> result = service.execution(request);

        assertThat((double) result.get("implementationShortfallBps")).isCloseTo(200d, within(0.001d));
        assertThat((double) result.get("slippageBps")).isCloseTo(99.00990099, within(0.001d));
        assertThat((double) result.get("notional")).isCloseTo(1020d, within(0.001d));
        assertThat(result.get("side")).isEqualTo("BUY");
    }

    @Test
    @DisplayName("执行归因：空参数快速失败")
    void executionRequiresArgs() {
        ExecutionRequest request = new ExecutionRequest();
        assertThatThrownBy(() -> service.execution(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static BrinsonLine line(String group, String wP, String rP, String wB, String rB) {
        BrinsonLine line = new BrinsonLine();
        line.setGroup(group);
        line.setPortfolioWeight(new BigDecimal(wP));
        line.setPortfolioReturn(new BigDecimal(rP));
        line.setBenchmarkWeight(new BigDecimal(wB));
        line.setBenchmarkReturn(new BigDecimal(rB));
        return line;
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}