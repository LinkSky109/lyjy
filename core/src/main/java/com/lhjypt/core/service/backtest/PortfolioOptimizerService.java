package com.lhjypt.core.service.backtest;

import com.lhjypt.core.model.backtest.BacktestConfig;
import com.lhjypt.core.model.backtest.BacktestResult;
import com.lhjypt.core.model.backtest.PortfolioOptimizeRequest;
import com.lhjypt.core.model.backtest.PortfolioOptimizeResult;
import com.lhjypt.core.model.backtest.PositionSnapshot;
import com.lhjypt.core.model.market.BarData;
import com.lhjypt.core.model.market.TickData;
import com.lhjypt.core.service.market.MarketDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 组合层优化（OBS-014）：多策略资金分配。
 *
 * <p>对每个成员策略在真实行情上回测，取每 bar 权益收益序列，按方法分配权重：
 * 等权 / 风险平价（反波动率）/ 最大夏普（权重网格），输出组合指标与等权对照。
 */
@Service
public class PortfolioOptimizerService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioOptimizerService.class);
    /** 1h 年化因子（bars/年） */
    private static final double BARS_PER_YEAR = 24d * 365d;

    private final BacktestEngine backtestEngine;
    private final MarketDataRepository marketDataRepository;

    public PortfolioOptimizerService(BacktestEngine backtestEngine,
                                     MarketDataRepository marketDataRepository) {
        this.backtestEngine = backtestEngine;
        this.marketDataRepository = marketDataRepository;
    }

    public PortfolioOptimizeResult optimize(PortfolioOptimizeRequest request) {
        if (request.getStrategies() == null || request.getStrategies().isEmpty()) {
            throw new IllegalArgumentException("请至少提供一个组合成员策略");
        }
        if (request.getStrategies().size() > 4) {
            throw new IllegalArgumentException("组合成员上限 4 个（网格寻优防爆）");
        }
        String frequency = request.getFrequency() == null ? "1h" : request.getFrequency();
        String mode = "ohlc".equalsIgnoreCase(request.getMode()) ? "ohlc" : "close";
        String start = request.getStartTime() == null ? "2000-01-01T00:00:00" : request.getStartTime();
        String end = request.getEndTime() == null ? "2099-12-31T23:59:59" : request.getEndTime();

        // 逐成员回测取收益序列
        List<double[]> returnsList = new ArrayList<>();
        List<PortfolioOptimizeResult.MemberResult> members = new ArrayList<>();
        for (PortfolioOptimizeRequest.Member member : request.getStrategies()) {
            String symbol = member.getSymbol() == null ? "BTC_USDT" : member.getSymbol();
            int fast = member.getFast() == null ? 5 : member.getFast();
            int slow = member.getSlow() == null ? 20 : member.getSlow();
            List<TickData> ticks = loadTicks(symbol, frequency, start, end, mode);
            if (ticks.isEmpty()) {
                throw new IllegalStateException("无真实行情数据: " + symbol
                        + "，请先 POST /api/market/real/refresh");
            }
            BacktestConfig config = new BacktestConfig();
            config.setStrategyName("BUILTIN_SMA");
            config.setInitialCapital(new BigDecimal("1000000"));
            config.setLookaheadDetectionEnabled(false);
            BacktestResult result = backtestEngine.run(config, ticks,
                    new BuiltInStrategy(fast, slow, BigDecimal.ONE));

            double[] returns = equityReturns(result.getSnapshots());
            returnsList.add(returns);
            PortfolioOptimizeResult.MemberResult mr = new PortfolioOptimizeResult.MemberResult();
            mr.setSymbol(symbol);
            mr.setFast(fast);
            mr.setSlow(slow);
            mr.setAnnualReturn(round(mean(returns) * BARS_PER_YEAR));
            mr.setAnnualVolatility(round(std(returns) * Math.sqrt(BARS_PER_YEAR)));
            mr.setSharpe(mr.getAnnualVolatility() > 1e-9
                    ? round(mr.getAnnualReturn() / mr.getAnnualVolatility()) : 0);
            members.add(mr);
        }

        int bars = returnsList.stream().mapToInt(a -> a.length).min().orElse(0);
        if (bars < 10) {
            throw new IllegalStateException("有效收益样本不足（" + bars + "），请扩大数据窗");
        }

        String method = request.getMethod() == null
                ? PortfolioOptimizeRequest.METHOD_EQUAL
                : request.getMethod().trim().toUpperCase();
        double[] weights = switch (method) {
            case PortfolioOptimizeRequest.METHOD_RISK_PARITY -> riskParityWeights(members);
            case PortfolioOptimizeRequest.METHOD_MAX_SHARPE -> maxSharpeWeights(
                    returnsList, bars, request.getStep() == null ? 0.1 : request.getStep());
            default -> equalWeights(members.size());
        };
        double[] equal = equalWeights(members.size());

        PortfolioOptimizeResult result = new PortfolioOptimizeResult();
        result.setMethod(method);
        result.setFrequency(frequency);
        result.setMode(mode);
        result.setBars(bars);
        for (int i = 0; i < members.size(); i++) {
            members.get(i).setWeight(round(weights[i]));
        }
        result.setMembers(members);

        double[] portfolioReturns = combine(returnsList, weights, bars);
        result.setPortfolioReturn(round(mean(portfolioReturns) * BARS_PER_YEAR));
        result.setPortfolioVolatility(round(std(portfolioReturns) * Math.sqrt(BARS_PER_YEAR)));
        result.setPortfolioSharpe(sharpeOf(portfolioReturns));
        result.setEqualWeightSharpe(sharpeOf(combine(returnsList, equal, bars)));
        log.info("组合优化完成: {} 成员 {} 组合夏普 {} 等权夏普 {}",
                method, members.size(), result.getPortfolioSharpe(), result.getEqualWeightSharpe());
        return result;
    }

    private List<TickData> loadTicks(String symbol, String frequency,
                                     String start, String end, String mode) {
        List<BarData> bars = marketDataRepository.queryBars(symbol, frequency, start, end, 50000);
        return "ohlc".equalsIgnoreCase(mode)
                ? BarTicksMapper.fromBars(bars, BarTicksMapper.MODE_OHLC)
                : BarTicksMapper.fromBars(bars, BarTicksMapper.MODE_CLOSE);
    }

    private static double[] equityReturns(List<PositionSnapshot> snapshots) {
        if (snapshots == null || snapshots.size() < 2) {
            return new double[0];
        }
        double[] returns = new double[snapshots.size() - 1];
        for (int i = 1; i < snapshots.size(); i++) {
            double prev = snapshots.get(i - 1).getTotalAsset() == null
                    ? 0 : snapshots.get(i - 1).getTotalAsset().doubleValue();
            double curr = snapshots.get(i).getTotalAsset() == null
                    ? 0 : snapshots.get(i).getTotalAsset().doubleValue();
            returns[i - 1] = prev > 1e-9 ? (curr - prev) / prev : 0.0;
        }
        return returns;
    }

    private static double[] combine(List<double[]> returnsList, double[] weights, int bars) {
        double[] out = new double[bars];
        for (int i = 0; i < bars; i++) {
            double value = 0;
            for (int k = 0; k < returnsList.size(); k++) {
                value += weights[k] * returnsList.get(k)[i];
            }
            out[i] = value;
        }
        return out;
    }

    private static double[] equalWeights(int n) {
        double[] w = new double[n];
        for (int i = 0; i < n; i++) {
            w[i] = 1.0 / n;
        }
        return w;
    }

    /** 反波动率权重（风险平价简化实现）。 */
    private static double[] riskParityWeights(List<PortfolioOptimizeResult.MemberResult> members) {
        double[] w = new double[members.size()];
        double sum = 0;
        for (int i = 0; i < members.size(); i++) {
            double vol = Math.max(members.get(i).getAnnualVolatility(), 1e-6);
            w[i] = 1.0 / vol;
            sum += w[i];
        }
        for (int i = 0; i < w.length; i++) {
            w[i] = sum > 0 ? w[i] / sum : 1.0 / members.size();
        }
        return w;
    }

    /** 最大夏普：单纯形网格搜索（步长 step，权重和为 1，非负）。 */
    static double[] maxSharpeWeights(List<double[]> returnsList, int bars, double step) {
        double stepSafe = step < 0.05 ? 0.05 : Math.min(step, 0.5);
        int n = returnsList.size();
        if (n == 1) {
            return new double[]{1.0};
        }
        double[] best = equalWeights(n);
        double bestSharpe = Double.NEGATIVE_INFINITY;
        List<double[]> candidates = gridWeights(n, stepSafe);
        for (double[] w : candidates) {
            double[] combined = combine(returnsList, w, bars);
            double vol = std(combined);
            if (vol <= 1e-12) {
                continue;
            }
            double sharpe = mean(combined) / vol;
            if (sharpe > bestSharpe) {
                bestSharpe = sharpe;
                best = w;
            }
        }
        return best;
    }

    /** 生成 n 维非负、和为 1 的网格权重（步长 step）。 */
    static List<double[]> gridWeights(int n, double step) {
        List<double[]> result = new ArrayList<>();
        int units = (int) Math.round(1.0 / step);
        fill(result, new int[n], 0, units, units, step);
        return result;
    }

    private static void fill(List<double[]> result, int[] current, int index,
                             int remaining, int units, double step) {
        if (index == current.length - 1) {
            current[index] = remaining;
            double[] w = new double[current.length];
            for (int i = 0; i < current.length; i++) {
                w[i] = current[i] * step;
            }
            result.add(w);
            return;
        }
        for (int used = 0; used <= remaining; used++) {
            current[index] = used;
            fill(result, current, index + 1, remaining - used, units, step);
        }
        current[index] = 0;
    }

    private static double mean(double[] values) {
        if (values.length == 0) {
            return 0;
        }
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    private static double std(double[] values) {
        if (values.length < 2) {
            return 0;
        }
        double m = mean(values);
        double sum = 0;
        for (double v : values) {
            sum += (v - m) * (v - m);
        }
        return Math.sqrt(sum / (values.length - 1));
    }

    private static double round(double value) {
        return Math.round(value * 1e6) / 1e6;
    }

    /** 统一夏普计算（保证等权对照与组合口径一致）。 */
    private static double sharpeOf(double[] returns) {
        double vol = std(returns) * Math.sqrt(BARS_PER_YEAR);
        if (vol <= 1e-9) {
            return 0;
        }
        return round(round(mean(returns) * BARS_PER_YEAR) / round(vol));
    }
}