package com.lhjypt.core.service.var;

import com.lhjypt.core.common.exception.TradeRejectedException;
import com.lhjypt.core.model.var.VarPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VarCalculatorTest {

    @Test
    @DisplayName("线性插值分位数与已知取值一致")
    void quantileMatchesKnownValues() {
        double[] sorted = {1, 2, 3, 4, 5};
        assertThat(VarCalculator.quantile(sorted, 0.0)).isEqualTo(1);
        assertThat(VarCalculator.quantile(sorted, 1.0)).isEqualTo(5);
        assertThat(VarCalculator.quantile(sorted, 0.5)).isEqualTo(3);
        assertThat(VarCalculator.quantile(sorted, 0.25)).isEqualTo(2);
    }

    @Test
    @DisplayName("逆正态分位与标准值一致")
    void zScoreMatchesStandardValues() {
        assertThat(VarCalculator.zScore(0.95)).isCloseTo(1.6449, within(0.001));
        assertThat(VarCalculator.zScore(0.99)).isCloseTo(2.3263, within(0.001));
        assertThat(VarCalculator.zScore(0.90)).isCloseTo(1.2816, within(0.001));
        assertThat(VarCalculator.zScore(0.5)).isCloseTo(0.0, within(0.0001));
    }

    @Test
    @DisplayName("无持仓时 VaR 为零且样本为零")
    void emptyPositionsReturnZero() {
        VarComputation result = VarCalculator.compute(
                List.of(), Map.of(), "HISTORICAL", 0.95, 1);
        assertThat(result.varValue()).isZero();
        assertThat(result.sampleCount()).isZero();
    }

    @Test
    @DisplayName("收益样本不足 60 抛 INSUFFICIENT_DATA，不产出虚假数值")
    void insufficientSamplesThrows() {
        List<VarPosition> positions = List.of(
                new VarPosition("rb", "LONG", new BigDecimal("2")));
        Map<String, List<BigDecimal>> closes = Map.of("rb", series(10, 100d, 1.001d));

        assertThatThrownBy(() -> VarCalculator.compute(
                positions, closes, "HISTORICAL", 0.95, 1))
                .isInstanceOf(TradeRejectedException.class)
                .hasMessageContaining("INSUFFICIENT_DATA");
    }

    @Test
    @DisplayName("历史模拟：下跌市场多头产生正 VaR，且置信度越高 VaR 越大")
    void historicalVarPositiveAndMonotonicInConfidence() {
        List<VarPosition> positions = List.of(
                new VarPosition("rb", "LONG", new BigDecimal("10")));
        Map<String, List<BigDecimal>> closes = new LinkedHashMap<>();
        // 30 天上涨 4%，30 天下跌 4% 交错的确定性行情
        var series = new ArrayList<BigDecimal>();
        double price = 3500d;
        for (int i = 0; i < 121; i++) {
            series.add(BigDecimal.valueOf(price).setScale(4, RoundingMode.HALF_UP));
            price *= (i % 2 == 0) ? 1.02 : 0.985;
        }
        closes.put("rb", series);

        VarComputation var95 = VarCalculator.compute(
                positions, closes, "HISTORICAL", 0.95, 1);
        VarComputation var99 = VarCalculator.compute(
                positions, closes, "HISTORICAL", 0.99, 1);

        assertThat(var95.sampleCount()).isEqualTo(120);
        assertThat(var95.varValue()).isPositive();
        assertThat(var99.varValue()).isGreaterThanOrEqualTo(var95.varValue());
    }

    @Test
    @DisplayName("持有期按平方根时间缩放")
    void horizonScalesBySquareRootOfTime() {
        List<VarPosition> positions = List.of(
                new VarPosition("rb", "LONG", new BigDecimal("10")));
        Map<String, List<BigDecimal>> closes = new LinkedHashMap<>();
        var series = new ArrayList<BigDecimal>();
        double price = 3500d;
        for (int i = 0; i < 121; i++) {
            series.add(BigDecimal.valueOf(price).setScale(4, RoundingMode.HALF_UP));
            price *= (i % 3 == 0) ? 1.03 : 0.99;
        }
        closes.put("rb", series);

        VarComputation daily = VarCalculator.compute(
                positions, closes, "HISTORICAL", 0.95, 1);
        VarComputation fourDay = VarCalculator.compute(
                positions, closes, "HISTORICAL", 0.95, 4);

        assertThat(fourDay.varValue()).isCloseTo(daily.varValue() * 2, within(0.0001));
    }

    @Test
    @DisplayName("上涨市场空头 VaR 为正、多头因收益为正而 VaR 归零")
    void shortPositionInvertsSign() {
        List<VarPosition> longLeg = List.of(
                new VarPosition("rb", "LONG", new BigDecimal("10")));
        List<VarPosition> shortLeg = List.of(
                new VarPosition("rb", "SHORT", new BigDecimal("10")));
        Map<String, List<BigDecimal>> closes = new LinkedHashMap<>();
        var series = new ArrayList<BigDecimal>();
        double price = 3500d;
        for (int i = 0; i < 121; i++) {
            series.add(BigDecimal.valueOf(price).setScale(4, RoundingMode.HALF_UP));
            price *= 1.01; // 持续上涨
        }
        closes.put("rb", series);

        VarComputation longVar = VarCalculator.compute(
                longLeg, closes, "HISTORICAL", 0.95, 1);
        VarComputation shortVar = VarCalculator.compute(
                shortLeg, closes, "HISTORICAL", 0.95, 1);

        assertThat(longVar.varValue()).isZero();
        assertThat(shortVar.varValue()).isPositive();
    }

    @Test
    @DisplayName("方差协方差法产出非负 VaR")
    void varCovProducesPositiveValue() {
        List<VarPosition> positions = List.of(
                new VarPosition("rb", "LONG", new BigDecimal("10")),
                new VarPosition("au", "SHORT", new BigDecimal("5")));
        Map<String, List<BigDecimal>> closes = new LinkedHashMap<>();
        closes.put("rb", alternatingSeries(121, 3500d, 1.02d, 0.985d));
        closes.put("au", alternatingSeries(121, 560d, 1.01d, 0.995d));

        VarComputation result = VarCalculator.compute(
                positions, closes, "VAR_COV", 0.95, 1);

        assertThat(result.sampleCount()).isEqualTo(120);
        assertThat(result.varValue()).isPositive();
    }

    private static List<BigDecimal> alternatingSeries(int size, double start,
                                                       double up, double down) {
        List<BigDecimal> values = new ArrayList<>(size);
        double price = start;
        for (int i = 0; i < size; i++) {
            values.add(BigDecimal.valueOf(price).setScale(4, RoundingMode.HALF_UP));
            price *= (i % 2 == 0) ? up : down;
        }
        return values;
    }

    private static List<BigDecimal> series(int size, double start, double factor) {
        List<BigDecimal> values = new ArrayList<>(size);
        double price = start;
        for (int i = 0; i < size; i++) {
            values.add(BigDecimal.valueOf(price).setScale(4, RoundingMode.HALF_UP));
            price *= factor;
        }
        return values;
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
