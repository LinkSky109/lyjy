package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
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

    /** 拉取真实 K 线并写入 ClickHouse bar_data（写操作需 ADMIN）。 */
    @PostMapping("/refresh")
    public Result<Integer> refresh(
            @RequestParam(required = false) String currencyPair,
            @RequestParam(required = false) String interval,
            @RequestParam(required = false) Integer limit) {
        return Result.success(
                realMarketDataService.refresh(currencyPair, interval, limit));
    }

    /** 源状态与最近刷新。 */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(realMarketDataService.status());
    }
}