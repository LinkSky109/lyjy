package com.lhjypt.core.service.backtest;

import com.lhjypt.core.model.backtest.BacktestConfig;
import com.lhjypt.core.model.backtest.OptimizeRequest;
import com.lhjypt.core.model.backtest.OptimizeResult;
import com.lhjypt.core.model.market.BarData;
import com.lhjypt.core.model.market.TickData;
import com.lhjypt.core.service.market.MarketDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 策略参数优化（OBS-011）：在真实行情上对双均线（快/慢周期）做网格寻优。
 * 约束：回撤上限过滤 + 组合数上限防爆；目标函数（夏普/总收益/卡玛）排序，输出最优与对照。
 */
@Service
public class StrategyOptimizerService {

    private static final Logger log = LoggerFactory.getLogger(StrategyOptimizerService.class);
    private static final DateTimeFormatter ISO_LOCAL =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final BacktestEngine backtestEngine;
    private final MarketDataRepository marketDataRepository;

    public StrategyOptimizerService(BacktestEngine backtestEngine,
                                    MarketDataRepository marketDataRepository) {
        this.backtestEngine = backtestEngine;
        this.marketDataRepository = marketDataRepository;
    }

    public OptimizeResult optimize(OptimizeRequest request) {
        String symbol = request.getSymbol() == null ? "BTC_USDT" : request.getSymbol();
        String frequency = request.getFrequency() == null ? "1h" : request.getFrequency();
        String mode = "ohlc".equalsIgnoreCase(request.getMode()) ? "ohlc" : "close";
        String start = request.getStartTime() == null
                ? "2000-01-01T00:00:00" : request.getStartTime();
        String end = request.getEndTime() == null
                ? "2099-12-31T23:59:59" : request.getEndTime();
        BigDecimal quantity = request.getQuantity() == null
                ? BigDecimal.ONE : BigDecimal.valueOf(request.getQuantity());

        List<TickData> ticks = loadTicks(symbol, frequency, start, end, mode);
        if (ticks.isEmpty()) {
            throw new IllegalStateException("无真实行情数据: " + symbol + "@" + frequency
                    + "，请先 POST /api/market/real/refresh");
        }

        List<Integer> fasts = request.getFastOptions() == null
                ? List.of(3, 5, 8) : request.getFastOptions();
        List<Integer> slows = request.getSlowOptions() == null
                ? List.of(15, 20, 30) : request.getSlowOptions();
        int maxRuns = request.getMaxRuns() <= 0 ? 50 : request.getMaxRuns();

        OptimizeResult result = new OptimizeResult();
        result.setSymbol(symbol);
        result.setFrequency(frequency);
        result.setMode(mode);
        result.setObjective(upper(request.getObjective()));

        // 基准 5/20
        result.setBaseline(evaluate(ticks, 5, 20, quantity, result.getObjective(),
                start, end, frequency, mode));

        // 网格（防爆：若组合超限按 slow 步进截断）
        List<int[]> grid = buildGrid(fasts, slows, maxRuns);
        List<OptimizeResult.Run> runs = new ArrayList<>();
        int filtered = 0;
        double maxDd = request.getMaxDrawdownLimit() == null
                ? 0.2 : request.getMaxDrawdownLimit();
        for (int[] pair : grid) {
            OptimizeResult.Run run = evaluate(ticks, pair[0], pair[1], quantity,
                    result.getObjective(), start, end, frequency, mode);
            if (run == null) {
                continue; // 非法参数（slow<=fast）排除
            }
            if (maxDd > 0 && run.getMaxDrawdown() > maxDd) {
                filtered++;
                continue;
            }
            runs.add(run);
        }
        runs.sort(Comparator
                .comparingDouble((OptimizeResult.Run r) -> score(r, result.getObjective()))
                .reversed());
        result.setRuns(runs);
        result.setFilteredByDrawdown(filtered);
        result.setBarsUsed(ticks.size() / ("close".equalsIgnoreCase(mode) ? 1 : 4));
        if (!runs.isEmpty()) {
            result.setBest(runs.get(0));
        }
        log.info("策略优化完成: {}@{} 组合 {} 最优 {} ({}={}) 过滤 {}",
                symbol, frequency, runs.size(),
                result.getBest() == null ? "-" : result.getBest().getFast() + "/" + result.getBest().getSlow(),
                result.getObjective(),
                result.getBest() == null ? "-" : score(result.getBest(), result.getObjective()),
                filtered);
        return result;
    }

    private static double score(OptimizeResult.Run run, String objective) {
        return switch (upper(objective)) {
            case OptimizeRequest.OBJ_RETURN -> run.getTotalReturn();
            case OptimizeRequest.OBJ_CALMAR -> safeCalmar(run);
            default -> run.getSharpe();
        };
    }

    private static double safeCalmar(OptimizeResult.Run run) {
        return run.getMaxDrawdown() > 0.0001
                ? run.getTotalReturn() / run.getMaxDrawdown() : 0.0;
    }

    private OptimizeResult.Run evaluate(List<TickData> ticks, int fast, int slow,
                                        BigDecimal quantity, String objective,
                                        String start, String end,
                                        String frequency, String mode) {
        // 参数非法（slow<=fast）返回 null（不参与寻优）
        if (slow <= fast) {
            return null;
        }
        BacktestConfig config = new BacktestConfig();
        config.setStrategyName("BUILTIN_SMA");
        config.setInitialCapital(new BigDecimal("1000000"));
        config.setLookaheadDetectionEnabled(false);
        var metrics = backtestEngine
                .run(config, ticks, new BuiltInStrategy(fast, slow, quantity))
                .getMetrics();
        double returnRate = metrics == null || metrics.getTotalReturn() == null
                ? 0 : metrics.getTotalReturn().doubleValue();
        double sharpe = metrics == null || metrics.getSharpeRatio() == null
                ? 0 : metrics.getSharpeRatio().doubleValue();
        double dd = metrics == null || metrics.getMaxDrawdown() == null
                ? 0 : metrics.getMaxDrawdown().doubleValue();
        int trades = metrics == null ? 0 : metrics.getTotalTrades();
        return new OptimizeResult.Run(fast, slow,
                Math.round(returnRate * 1e6) / 1e6,
                Math.round(sharpe * 1e4) / 1e4,
                Math.round(dd * 1e6) / 1e6,
                trades);
    }

    private List<TickData> loadTicks(String symbol, String frequency,
                                     String start, String end, String mode) {
        List<BarData> bars = marketDataRepository.queryBars(symbol, frequency, start, end, 50000);
        if ("ohlc".equalsIgnoreCase(mode)) {
            return BarTicksMapper.fromBars(bars, BarTicksMapper.MODE_OHLC);
        }
        return BarTicksMapper.fromBars(bars, BarTicksMapper.MODE_CLOSE);
    }

    private static List<int[]> buildGrid(List<Integer> fasts, List<Integer> slows, int maxRuns) {
        List<int[]> grid = new ArrayList<>();
        for (int fast : fasts) {
            for (int slow : slows) {
                if (grid.size() >= maxRuns) {
                    return grid;
                }
                grid.add(new int[]{fast, slow});
            }
        }
        return grid;
    }

    private static String upper(String value) {
        return value == null ? OptimizeRequest.OBJ_SHARPE : value.trim().toUpperCase();
    }
}