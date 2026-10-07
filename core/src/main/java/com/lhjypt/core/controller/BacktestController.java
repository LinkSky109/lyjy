package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.common.auth.UserContext;
import com.lhjypt.core.common.auth.UserContextHolder;
import com.lhjypt.core.model.backtest.BacktestConfig;
import com.lhjypt.core.model.backtest.BacktestResult;
import com.lhjypt.core.model.backtest.OptimizeRequest;
import com.lhjypt.core.model.backtest.OptimizeResult;
import com.lhjypt.core.model.backtest.StrategyOptimization;
import com.lhjypt.core.model.market.BarData;
import com.lhjypt.core.model.market.TickData;
import com.lhjypt.core.service.backtest.BacktestEngine;
import com.lhjypt.core.service.backtest.BarTicksMapper;
import com.lhjypt.core.service.backtest.StrategyOptimizerService;
import com.lhjypt.core.service.market.MarketDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 回测引擎API
 */
@RestController
@RequestMapping("/api/backtest")
@RequiredArgsConstructor
public class BacktestController {

    private final BacktestEngine backtestEngine;
    private final MarketDataRepository marketDataRepository;
    private final StrategyOptimizerService strategyOptimizerService;

    private static final DateTimeFormatter ISO_LOCAL =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** 策略参数优化：真实行情网格寻优（OBS-011/012，多标的返回列表）。 */
    @PostMapping("/optimize")
    public Result<List<OptimizeResult>> optimize(@RequestBody OptimizeRequest request) {
        return Result.success(strategyOptimizerService.optimize(request));
    }

    /** 应用最优参数：复验回测（可选把参数落档到策略 params_json）。 */
    @PostMapping("/optimize/apply")
    public Result<java.util.Map<String, Object>> applyOptimization(
            @RequestParam String batchId,
            @RequestParam(required = false) String strategyId) {
        UserContext context = UserContextHolder.get();
        return Result.success(strategyOptimizerService.apply(batchId, strategyId,
                context.hasAnyRole(UserContext.ROLE_ADMIN), context.userId()));
    }

    /** 优化历史存档。 */
    @GetMapping("/optimize/history")
    public Result<List<StrategyOptimization>> optimizationHistory(
            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(strategyOptimizerService.history(limit));
    }

    /**
     * 同步运行回测（使用模拟数据）
     */
    @PostMapping("/run")
    public Result<BacktestResult> run(@RequestBody BacktestConfig config) {
        // 使用模拟行情数据（实际应用中从ClickHouse加载）
        String symbol = config.getSymbols() != null && !config.getSymbols().isEmpty()
                ? config.getSymbols().get(0) : "rb2410";
        List<TickData> ticks = backtestEngine.generateMockTicks(symbol, 1000, new BigDecimal("3500"));
        return Result.success(backtestEngine.run(config, ticks));
    }

    /**
     * 使用真实行情回测：从 ClickHouse bar_data 读取真实 K 线。
     * 数据窗取 config.startTime/endTime（可空→全量最近）；频率默认 dataFrequency 或参数覆盖。
     */
    @PostMapping("/run-real")
    public Result<BacktestResult> runReal(@RequestBody BacktestConfig config,
                                          @RequestParam(required = false) String frequency,
                                          @RequestParam(defaultValue = "ohlc") String mode) {
        String symbol = config.getSymbols() != null && !config.getSymbols().isEmpty()
                ? config.getSymbols().get(0) : "BTC_USDT";
        String freq = frequency != null && !frequency.isBlank() ? frequency
                : (config.getDataFrequency() != null && !config.getDataFrequency().isBlank()
                        ? config.getDataFrequency() : "1h");
        String start = config.getStartTime() == null
                ? "2000-01-01T00:00:00" : config.getStartTime().format(ISO_LOCAL);
        String end = config.getEndTime() == null
                ? "2099-12-31T23:59:59" : config.getEndTime().format(ISO_LOCAL);

        List<BarData> bars = marketDataRepository.queryBars(symbol, freq, start, end, 50000);
        if (bars.isEmpty()) {
            return Result.error(422, "无真实行情数据: " + symbol + "@" + freq
                    + "，请先 POST /api/market/real/refresh");
        }
        List<TickData> ticks = "close".equalsIgnoreCase(mode)
                ? BarTicksMapper.fromBars(bars, BarTicksMapper.MODE_CLOSE)
                : BarTicksMapper.fromBars(bars, BarTicksMapper.MODE_OHLC);
        return Result.success(backtestEngine.run(config, ticks));
    }

    /**
     * 使用指定行情数据运行回测
     */
    @PostMapping("/run-with-data")
    public Result<BacktestResult> runWithData(@RequestParam String strategyName,
                                              @RequestParam(defaultValue = "1000000") BigDecimal capital,
                                              @RequestBody List<TickData> ticks) {
        BacktestConfig config = new BacktestConfig();
        config.setStrategyName(strategyName);
        config.setInitialCapital(capital);
        return Result.success(backtestEngine.run(config, ticks));
    }

    /**
     * 异步运行回测
     */
    @PostMapping("/run-async")
    public Result<String> runAsync(@RequestBody BacktestConfig config) {
        String symbol = config.getSymbols() != null && !config.getSymbols().isEmpty()
                ? config.getSymbols().get(0) : "rb2410";
        List<TickData> ticks = backtestEngine.generateMockTicks(symbol, 10000, new BigDecimal("3500"));
        return Result.success(backtestEngine.runAsync(config, ticks));
    }

    /**
     * 查询回测结果
     */
    @GetMapping("/result/{backtestId}")
    public Result<BacktestResult> getResult(@PathVariable String backtestId) {
        BacktestResult result = backtestEngine.getResult(backtestId);
        if (result == null) {
            return Result.error("回测任务不存在: " + backtestId);
        }
        return Result.success(result);
    }

    /**
     * 生成模拟行情数据
     */
    @GetMapping("/mock-ticks")
    public Result<List<TickData>> generateMockTicks(
            @RequestParam(defaultValue = "rb2410") String symbol,
            @RequestParam(defaultValue = "100") int count,
            @RequestParam(defaultValue = "3500") BigDecimal basePrice) {
        return Result.success(backtestEngine.generateMockTicks(symbol, count, basePrice));
    }
}
