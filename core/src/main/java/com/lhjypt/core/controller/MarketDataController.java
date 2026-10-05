package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.model.market.BarData;
import com.lhjypt.core.model.market.TickData;
import com.lhjypt.core.service.market.MarketDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 行情数据API
 */
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketDataController {

    private final MarketDataService marketDataService;

    /**
     * 启动行情服务
     */
    @PostMapping("/start")
    public Result<Void> start() {
        marketDataService.start();
        return Result.success();
    }

    /**
     * 停止行情服务
     */
    @PostMapping("/stop")
    public Result<Void> stop() {
        marketDataService.stop();
        return Result.success();
    }

    /**
     * 行情服务状态
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(Map.of(
                "running", marketDataService.isRunning(),
                "subscribed", marketDataService.getSubscribedSymbols()
        ));
    }

    /**
     * 订阅标的
     */
    @PostMapping("/subscribe")
    public Result<Void> subscribe(@RequestParam String symbol) {
        marketDataService.subscribe(symbol);
        return Result.success();
    }

    /**
     * 批量订阅
     */
    @PostMapping("/subscribe/batch")
    public Result<Void> subscribeBatch(@RequestBody List<String> symbols) {
        marketDataService.subscribeAll(symbols);
        return Result.success();
    }

    /**
     * 取消订阅
     */
    @PostMapping("/unsubscribe")
    public Result<Void> unsubscribe(@RequestParam String symbol) {
        marketDataService.unsubscribe(symbol);
        return Result.success();
    }

    /**
     * 获取最新tick
     */
    @GetMapping("/tick/{symbol}")
    public Result<TickData> latestTick(@PathVariable String symbol) {
        return Result.success(marketDataService.getLatestTick(symbol));
    }

    /**
     * 查询历史tick
     */
    @GetMapping("/ticks")
    public Result<List<TickData>> queryTicks(
            @RequestParam String symbol,
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(defaultValue = "100") int limit) {
        return Result.success(marketDataService.queryTicks(symbol, startTime, endTime, limit));
    }

    /**
     * 查询历史K线
     */
    @GetMapping("/bars")
    public Result<List<BarData>> queryBars(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "1m") String frequency,
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(defaultValue = "100") int limit) {
        return Result.success(marketDataService.queryBars(symbol, frequency, startTime, endTime, limit));
    }

    /**
     * 获取已订阅标的列表
     */
    @GetMapping("/subscribed")
    public Result<Set<String>> subscribed() {
        return Result.success(marketDataService.getSubscribedSymbols());
    }
}
