package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.model.market.BarData;
import com.lhjypt.core.model.market.TickData;
import com.lhjypt.core.service.market.MarketDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
     * 统一行情快照：最新价、五档盘口与新鲜度（staleMs）。
     * 无 tick 时返回 data=null，由前端显示空状态。
     */
    @GetMapping("/quote/{symbol}")
    public Result<Map<String, Object>> quote(@PathVariable String symbol) {
        TickData tick = marketDataService.getLatestTick(symbol);
        if (tick == null) {
            return Result.success(null);
        }
        Map<String, Object> quote = new LinkedHashMap<>();
        quote.put("symbol", tick.getSymbol());
        quote.put("lastPrice", tick.getLastPrice());
        quote.put("preClose", tick.getPreClose());
        quote.put("open", tick.getOpen());
        quote.put("high", tick.getHigh());
        quote.put("low", tick.getLow());
        quote.put("volume", tick.getVolume());
        quote.put("turnover", tick.getTurnover());
        quote.put("openInterest", tick.getOpenInterest());
        quote.put("bidPrice1", tick.getBidPrice1());
        quote.put("bidPrice2", tick.getBidPrice2());
        quote.put("bidPrice3", tick.getBidPrice3());
        quote.put("bidPrice4", tick.getBidPrice4());
        quote.put("bidPrice5", tick.getBidPrice5());
        quote.put("bidVolume1", tick.getBidVolume1());
        quote.put("bidVolume2", tick.getBidVolume2());
        quote.put("bidVolume3", tick.getBidVolume3());
        quote.put("bidVolume4", tick.getBidVolume4());
        quote.put("bidVolume5", tick.getBidVolume5());
        quote.put("askPrice1", tick.getAskPrice1());
        quote.put("askPrice2", tick.getAskPrice2());
        quote.put("askPrice3", tick.getAskPrice3());
        quote.put("askPrice4", tick.getAskPrice4());
        quote.put("askPrice5", tick.getAskPrice5());
        quote.put("askVolume1", tick.getAskVolume1());
        quote.put("askVolume2", tick.getAskVolume2());
        quote.put("askVolume3", tick.getAskVolume3());
        quote.put("askVolume4", tick.getAskVolume4());
        quote.put("askVolume5", tick.getAskVolume5());
        quote.put("tickTime", tick.getTickTime());
        quote.put("recvTime", tick.getRecvTime());
        LocalDateTime basis = tick.getRecvTime() != null ? tick.getRecvTime() : tick.getTickTime();
        quote.put("staleMs", basis == null ? null
                : Math.max(0, Duration.between(basis, LocalDateTime.now()).toMillis()));
        return Result.success(quote);
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
