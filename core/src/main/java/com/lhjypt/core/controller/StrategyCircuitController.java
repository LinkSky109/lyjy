package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.common.auth.UserContextHolder;
import com.lhjypt.core.model.strategy.StrategyCircuit;
import com.lhjypt.core.service.strategy.StrategyCircuitBreakerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 策略回撤熔断 API（OBS-016）。
 */
@RestController
@RequestMapping("/api/strategy")
@RequiredArgsConstructor
public class StrategyCircuitController {

    private final StrategyCircuitBreakerService circuitBreakerService;

    @PostMapping("/{strategyId}/circuit")
    public Result<StrategyCircuit> setCircuit(
            @PathVariable String strategyId,
            @RequestParam(required = false) BigDecimal maxDrawdownPct,
            @RequestParam(defaultValue = "true") boolean enabled) {
        return Result.success(circuitBreakerService.setCircuit(
                strategyId, maxDrawdownPct, enabled, UserContextHolder.get()));
    }

    @GetMapping("/circuit/list")
    public Result<List<StrategyCircuit>> list() {
        return Result.success(circuitBreakerService.list());
    }
}