package com.lhjypt.core.service.var;

import com.lhjypt.core.common.exception.TradeRejectedException;
import com.lhjypt.core.model.var.VarPosition;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * VaR 计算引擎（PRD §5.7）。
 *
 * <p>输入带方向的持仓腿与各标的历史日线收盘价序列（升序），
 * 先构造组合日损益序列，再按方法取分位数：
 * <ul>
 *   <li>HISTORICAL：历史模拟法，取 1-confidence 分位数；</li>
 *   <li>VAR_COV：方差协方差法，σ² = wᵀΣw，VaR = z_confidence · σ。</li>
 * </ul>
 * 持有期按平方根时间缩放。样本不足（收益样本 &lt; 60）抛 {@code INSUFFICIENT_DATA}。
 */
public final class VarCalculator {

    /** 最少收益样本数（对齐 PRD「行情数据不足（&lt; 60 个样本）」）。 */
    public static final int MIN_SAMPLES = 60;

    private VarCalculator() {
    }

    public static VarComputation compute(List<VarPosition> positions,
                                         Map<String, List<BigDecimal>> closesBySymbol,
                                         String method,
                                         double confidence,
                                         int horizonDays) {
        List<VarPosition> legs = new ArrayList<>();
        for (VarPosition position : positions) {
            if (position != null && position.quantity() != null
                    && position.quantity().signum() != 0) {
                legs.add(position);
            }
        }
        if (legs.isEmpty()) {
            return new VarComputation(method, confidence, horizonDays,
                    0d, 0, 0d, 0d, 0d, 0d, 0d);
        }

        int minLength = Integer.MAX_VALUE;
        for (VarPosition leg : legs) {
            List<BigDecimal> closes = closesBySymbol.get(leg.symbol());
            if (closes == null || closes.isEmpty()) {
                throw insufficient("标的 " + leg.symbol() + " 无历史行情");
            }
            minLength = Math.min(minLength, closes.size());
        }

        int sampleCount = minLength - 1;
        if (sampleCount < MIN_SAMPLES) {
            throw insufficient("收益样本 " + sampleCount + " 少于 " + MIN_SAMPLES + " 个");
        }

        int legCount = legs.size();
        double[][] returns = new double[legCount][sampleCount];
        double[] lastClose = new double[legCount];
        for (int i = 0; i < legCount; i++) {
            List<BigDecimal> closes = closesBySymbol.get(legs.get(i).symbol());
            List<BigDecimal> tail = closes.subList(closes.size() - minLength, closes.size());
            lastClose[i] = tail.get(minLength - 1).doubleValue();
            for (int t = 0; t < sampleCount; t++) {
                double previous = tail.get(t).doubleValue();
                double current = tail.get(t + 1).doubleValue();
                returns[i][t] = previous == 0d ? 0d : current / previous - 1d;
            }
        }

        double[] notional = new double[legCount];
        for (int i = 0; i < legCount; i++) {
            notional[i] = legs.get(i).signedQuantity() * lastClose[i];
        }

        double[] pnl = new double[sampleCount];
        for (int t = 0; t < sampleCount; t++) {
            double value = 0d;
            for (int i = 0; i < legCount; i++) {
                value += notional[i] * returns[i][t];
            }
            pnl[t] = value;
        }

        double scale = Math.sqrt(Math.max(horizonDays, 1));
        double varValue;
        double percentilePnl;
        if ("VAR_COV".equalsIgnoreCase(method)) {
            double sigma = portfolioSigma(notional, returns, sampleCount);
            double z = zScore(confidence);
            varValue = z * sigma * scale;
            percentilePnl = -z * sigma;
        } else {
            double[] sorted = pnl.clone();
            Arrays.sort(sorted);
            double alpha = 1d - confidence;
            percentilePnl = quantile(sorted, alpha);
            varValue = Math.max(0d, -percentilePnl) * scale;
        }

        double mean = mean(pnl);
        double std = stdDev(pnl, mean);
        double min = Arrays.stream(pnl).min().orElse(0d);
        double max = Arrays.stream(pnl).max().orElse(0d);

        return new VarComputation(method, confidence, horizonDays,
                varValue, sampleCount, mean, std, percentilePnl, min, max);
    }

    /** 组合方差：σ² = Σi Σj w_i · w_j · cov(i,j)。 */
    private static double portfolioSigma(double[] notional, double[][] returns, int n) {
        int k = notional.length;
        double[] mean = new double[k];
        for (int i = 0; i < k; i++) {
            mean[i] = mean(returns[i]);
        }
        double variance = 0d;
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < k; j++) {
                double covariance = 0d;
                for (int t = 0; t < n; t++) {
                    covariance += (returns[i][t] - mean[i]) * (returns[j][t] - mean[j]);
                }
                covariance /= Math.max(n - 1, 1);
                variance += notional[i] * notional[j] * covariance;
            }
        }
        return Math.sqrt(Math.max(variance, 0d));
    }

    /** 线性插值分位数（sorted 升序，alpha ∈ [0,1]）。 */
    public static double quantile(double[] sorted, double alpha) {
        if (sorted.length == 0) {
            return 0d;
        }
        double position = alpha * (sorted.length - 1);
        int low = (int) Math.floor(position);
        int high = (int) Math.ceil(position);
        if (low == high) {
            return sorted[low];
        }
        return sorted[low] + (sorted[high] - sorted[low]) * (position - low);
    }

    /** 标准正态分布逆函数（Acklam 算法），返回 Phi^{-1}(p)。 */
    public static double zScore(double p) {
        final double[] a = {-3.969683028665376e+01, 2.209460984245205e+02,
                -2.759285104469687e+02, 1.383577518672690e+02,
                -3.066479806614716e+01, 2.506628277459239e+00};
        final double[] b = {-5.447609879822406e+01, 1.615858368580409e+02,
                -1.556989798598866e+02, 6.680131188771972e+01,
                -1.328068155288572e+01};
        final double[] c = {-7.784894002430293e-03, -3.223964580411365e-01,
                -2.400758277161838e+00, -2.549732539343734e+00,
                4.374664141464968e+00, 2.938163982698783e+00};
        final double[] d = {7.784695709041462e-03, 3.224671290700398e-01,
                2.445134137142996e+00, 3.754408661907416e+00};
        double plow = 0.02425;
        double phigh = 1 - plow;
        double q;
        double r;
        double x;
        if (p < plow) {
            q = Math.sqrt(-2 * Math.log(p));
            x = (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5])
                    / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
        } else if (p <= phigh) {
            q = p - 0.5;
            r = q * q;
            x = (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q
                    / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1);
        } else {
            q = Math.sqrt(-2 * Math.log(1 - p));
            x = -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5])
                    / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
        }
        return x;
    }

    private static double mean(double[] values) {
        double sum = 0d;
        for (double value : values) {
            sum += value;
        }
        return values.length == 0 ? 0d : sum / values.length;
    }

    private static double stdDev(double[] values, double mean) {
        if (values.length < 2) {
            return 0d;
        }
        double sum = 0d;
        for (double value : values) {
            double diff = value - mean;
            sum += diff * diff;
        }
        return Math.sqrt(sum / (values.length - 1));
    }

    private static TradeRejectedException insufficient(String detail) {
        return new TradeRejectedException("INSUFFICIENT_DATA",
                "INSUFFICIENT_DATA: 行情数据不足，无法计算 VaR（" + detail + "）");
    }
}
