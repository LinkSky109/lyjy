package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.service.market.impl.real.MarketDataQualityService;
import com.lhjypt.core.service.market.impl.real.RealMarketDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 真实行情源 API（OBS-005 Gate.io 现货 K 线）。
 */
@RestController
@RequestMapping("/api/market/real")
@RequiredArgsConstructor
public class RealMarketController {

    private final RealMarketDataService realMarketDataService;
    private final MarketDataQualityService qualityService;

    /** 拉取真实 K 线并写入 ClickHouse bar_data（写操作需 ADMIN）。 */
    @PostMapping("/refresh")
    public Result<Integer> refresh(
            @RequestParam(required = false) String currencyPair,
            @RequestParam(required = false) String interval,
            @RequestParam(required = false) Integer limit) {
        return Result.success(
                realMarketDataService.refresh(currencyPair, interval, limit));
    }

    /**
     * 分页回补更长历史（P4-001，写操作需 ADMIN）。
     *
     * @param total    目标条数（空 = pageSize × maxPages）
     * @param pageSize 每页条数（空取配置，上限 1000）
     * @param source   指定数据源（空则按配置顺序故障切换）
     */
    @PostMapping("/backfill")
    public Result<RealMarketDataService.BackfillResult> backfill(
            @RequestParam(required = false) String currencyPair,
            @RequestParam(required = false) String interval,
            @RequestParam(required = false) Integer total,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String source) {
        return Result.success(
                realMarketDataService.backfill(currencyPair, interval, total, pageSize, source));
    }

    /** 源状态与最近刷新。 */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(realMarketDataService.status());
    }

    /** 行情数据质量检测（缺口/异常/重复，P3-006）。 */
    @GetMapping("/quality")
    public Result<Map<String, Object>> quality(
            @RequestParam(required = false) String currencyPair,
            @RequestParam(required = false) String frequency,
            @RequestParam(defaultValue = "1000") int limit) {
        return Result.success(qualityService.check(
                currencyPair == null || currencyPair.isBlank() ? "BTC_USDT" : currencyPair,
                frequency == null || frequency.isBlank() ? "1h" : frequency,
                limit));
    }
}