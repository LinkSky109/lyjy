package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.common.auth.UserContextHolder;
import com.lhjypt.core.model.strategy.WatchdogEntry;
import com.lhjypt.core.service.strategy.StrategyWatchdogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 策略自动值守 API（OBS-010）：挂机运行托管。
 */
@RestController
@RequestMapping("/api/strategy")
@RequiredArgsConstructor
public class StrategyWatchdogController {

    private final StrategyWatchdogService watchdogService;

    @PostMapping("/{strategyId}/watchdog")
    public Result<WatchdogEntry> setWatchdog(
            @PathVariable String strategyId,
            @RequestParam(defaultValue = "true") boolean enabled,
            @RequestParam(required = false) Long checkIntervalMs) {
        return Result.success(watchdogService.setWatchdog(
                strategyId, enabled, checkIntervalMs, UserContextHolder.get()));
    }

    @GetMapping("/watchdog/list")
    public Result<List<WatchdogEntry>> list() {
        return Result.success(watchdogService.list());
    }
}