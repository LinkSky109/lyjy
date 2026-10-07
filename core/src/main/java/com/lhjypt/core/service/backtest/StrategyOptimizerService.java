package com.lhjypt.core.service.backtest;

import com.alibaba.fastjson2.JSON;
import com.lhjypt.core.mapper.StrategyInfoMapper;
import com.lhjypt.core.mapper.StrategyOptimizationMapper;
import com.lhjypt.core.model.backtest.BacktestConfig;
import com.lhjypt.core.model.backtest.BacktestResult;
import com.lhjypt.core.model.backtest.OptimizeRequest;
import com.lhjypt.core.model.backtest.OptimizeResult;
import com.lhjypt.core.model.backtest.StrategyOptimization;
import com.lhjypt.core.model.market.BarData;
import com.lhjypt.core.model.market.TickData;
import com.lhjypt.core.model.strategy.StrategyInfo;
import com.lhjypt.core.service.market.MarketDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 策略参数优化（OBS-011/012）：真实行情网格寻优 + 多标的 + 存档 + 最优参数应用。
 */
@Service
public class StrategyOptimizerService {

    private static final Logger log = LoggerFactory.getLogger(StrategyOptimizerService.class);

    private final BacktestEngine backtestEngine;
    private final MarketDataRepository marketDataRepository;
    private final StrategyOptimizationMapper optimizationMapper;
    private final StrategyInfoMapper strategyInfoMapper;

    public StrategyOptimizerService(BacktestEngine backtestEngine,
                                    MarketDataRepository marketDataRepository,
                                    StrategyOptimizationMapper optimizationMapper,
                                    StrategyInfoMapper strategyInfoMapper) {
        this.backtestEngine = backtestEngine;
        this.marketDataRepository = marketDataRepository;
        this.optimizationMapper = optimizationMapper;
        this.strategyInfoMapper = strategyInfoMapper;
    }

    /** 多标的寻优（symbols 优先；否则单 symbol），每次寻优结果入库存档，返回各标的结果。 */
    public List<OptimizeResult> optimize(OptimizeRequest request) {
        List<String> symbols = new ArrayList<>();
        if (request.getSymbols() != null && !request.getSymbols().isEmpty()) {
            request.getSymbols().stream()
                    .filter(s -> s != null && !s.isBlank())
                    .forEach(symbols::add);
        }
        if (symbols.isEmpty()) {
            symbols.add(request.getSymbol() == null ? "BTC_USDT" : request.getSymbol());
        }
        String batchId = "OPT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        List<OptimizeResult> results = new ArrayList<>();
        for (String symbol : symbols) {
            try {
                OptimizeResult result = doOptimize(request, symbol);
                result.setBatchId(batchId);
                persist(result, request);
                results.add(result);
            } catch (Exception error) {
                // 单标的失败不中断整批（如该标的无真实行情）
                OptimizeResult failed = new OptimizeResult();
                failed.setSymbol(symbol);
                failed.setFrequency(request.getFrequency());
                failed.setMode(request.getMode());
                failed.setObjective(upper(request.getObjective()));
                failed.setBatchId(batchId);
                failed.setError(error.getMessage());
                results.add(failed);
                log.warn("标的寻优失败 {}: {}", symbol, error.getMessage());
            }
        }
        return results;
    }

    /** 应用最优参数：读存档 → 用最优参数复验回测（可选写入策略 params_json）。 */
    public Map<String, Object> apply(String batchId, String strategyId,
                                     boolean admin, String operator) {
        StrategyOptimization opt = optimizationMapper.selectByBatchId(batchId);
        if (opt == null) {
            throw new IllegalArgumentException("优化记录不存在: " + batchId);
        }
        if (opt.getBestFast() == null || opt.getBestSlow() == null) {
            throw new IllegalStateException("该优化无可应用的最优参数: " + batchId);
        }
        List<TickData> ticks = loadTicks(opt.getSymbol(), opt.getFrequency(),
                opt.getStartTime() == null ? "2000-01-01T00:00:00" : opt.getStartTime(),
                opt.getEndTime() == null ? "2099-12-31T23:59:59" : opt.getEndTime(),
                opt.getMode());
        if (ticks.isEmpty()) {
            throw new IllegalStateException("无真实行情数据: " + opt.getSymbol()
                    + "，请先 POST /api/market/real/refresh");
        }
        BacktestConfig config = new BacktestConfig();
        config.setStrategyName("BUILTIN_SMA");
        config.setInitialCapital(new BigDecimal("1000000"));
        config.setLookaheadDetectionEnabled(false);
        BacktestResult verify = backtestEngine.run(config, ticks,
                new BuiltInStrategy(opt.getBestFast(), opt.getBestSlow(), BigDecimal.ONE));

        boolean applied = false;
        if (strategyId != null && !strategyId.isBlank()) {
            StrategyInfo strategy = strategyInfoMapper.selectByStrategyId(strategyId);
            if (strategy == null) {
                throw new IllegalArgumentException("策略不存在: " + strategyId);
            }
            if (!admin && !strategy.getOwner().equals(operator)) {
                throw new IllegalStateException("无权更新该策略参数: " + strategyId);
            }
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("fast", opt.getBestFast());
            params.put("slow", opt.getBestSlow());
            params.put("source", "optimizer");
            params.put("batchId", batchId);
            params.put("objective", opt.getObjective());
            strategy.setParamsJson(JSON.toJSONString(params));
            strategy.setUpdateTime(LocalDateTime.now());
            strategyInfoMapper.updateById(strategy);
            applied = true;
            log.info("策略最优参数已落档: strategy={} fast={} slow={}",
                    strategyId, opt.getBestFast(), opt.getBestSlow());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("optimization", opt);
        result.put("applied", applied);
        result.put("strategyId", strategyId);
        result.put("verify", verify);
        return result;
    }

    public List<StrategyOptimization> history(int limit) {
        return optimizationMapper.selectRecent(limit <= 0 ? 20 : Math.min(limit, 200));
    }

    private void persist(OptimizeResult result, OptimizeRequest request) {
        try {
            StrategyOptimization record = new StrategyOptimization();
            record.setBatchId(result.getBatchId());
            record.setSymbol(result.getSymbol());
            record.setFrequency(result.getFrequency());
            record.setMode(result.getMode());
            record.setObjective(result.getObjective());
            OptimizeResult.Run best = result.getBest();
            if (best != null) {
                record.setBestFast(best.getFast());
                record.setBestSlow(best.getSlow());
                record.setBestReturn(BigDecimal.valueOf(best.getTotalReturn()));
                record.setBestSharpe(BigDecimal.valueOf(best.getSharpe()));
                record.setBestDrawdown(BigDecimal.valueOf(best.getMaxDrawdown()));
            }
            if (result.getBaseline() != null) {
                record.setBaselineReturn(BigDecimal.valueOf(result.getBaseline().getTotalReturn()));
            }
            record.setBarsUsed(result.getBarsUsed());
            record.setStartTime(request.getStartTime());
            record.setEndTime(request.getEndTime());
            record.setRunsJson(JSON.toJSONString(result.getRuns()));
            record.setCreateTime(LocalDateTime.now());
            optimizationMapper.insert(record);
        } catch (Exception error) {
            log.warn("优化结果存档失败（不影响返回）: {}", error.getMessage());
        }
    }

    private OptimizeResult doOptimize(OptimizeRequest request, String symbol) {
        String frequency = request.getFrequency() == null ? "1h" : request.getFrequency();
        String mode = "ohlc".equalsIgnoreCase(request.getMode()) ? "ohlc" : "close";
        String start = request.getStartTime() == null ? "2000-01-01T00:00:00" : request.getStartTime();
        String end = request.getEndTime() == null ? "2099-12-31T23:59:59" : request.getEndTime();
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
        result.setBaseline(evaluate(ticks, 5, 20, quantity));
        if (result.getBaseline() == null) {
            result.setBaseline(new OptimizeResult.Run(5, 20, 0, 0, 0, 0));
        }

        List<OptimizeResult.Run> runs = new ArrayList<>();
        int filtered = 0;
        double maxDd = request.getMaxDrawdownLimit() == null ? 0.2 : request.getMaxDrawdownLimit();
        for (int[] pair : buildGrid(fasts, slows, maxRuns)) {
            OptimizeResult.Run run = evaluate(ticks, pair[0], pair[1], quantity);
            if (run == null) {
                continue;
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
        log.info("策略优化完成: {}@{} 组合 {} 最优 {} 过滤 {}",
                symbol, frequency, runs.size(),
                result.getBest() == null ? "-" : result.getBest().getFast() + "/" + result.getBest().getSlow(),
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
                                        BigDecimal quantity) {
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