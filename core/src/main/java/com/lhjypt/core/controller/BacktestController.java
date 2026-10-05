package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.model.backtest.BacktestConfig;
import com.lhjypt.core.model.backtest.BacktestResult;
import com.lhjypt.core.model.market.TickData;
import com.lhjypt.core.service.backtest.BacktestEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 回测引擎API
 */
@RestController
@RequestMapping("/api/backtest")
@RequiredArgsConstructor
public class BacktestController {

    private final BacktestEngine backtestEngine;

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
