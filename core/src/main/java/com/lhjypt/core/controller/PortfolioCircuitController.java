package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.common.auth.UserContextHolder;
import com.lhjypt.core.model.strategy.PortfolioCircuit;
import com.lhjypt.core.service.strategy.PortfolioCircuitService;
import com.lhjypt.core.service.strategy.StrategyRiskOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * 组合级风控 API（OBS-018）。
 */
@RestController
@RequestMapping("/api/strategy")
@RequiredArgsConstructor
public class PortfolioCircuitController {

    private final PortfolioCircuitService portfolioCircuitService;
    private final StrategyRiskOverviewService riskOverviewService;

    @PostMapping("/portfolio-circuit")
    public Result<PortfolioCircuit> setCircuit(
            @RequestParam String portfolioKey,
            @RequestParam String members,
            @RequestParam(required = false) BigDecimal maxDrawdownPct,
            @RequestParam(defaultValue = "true") boolean enabled) {
        List<String> parsed = Arrays.stream(members.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        return Result.success(portfolioCircuitService.setCircuit(
                portfolioKey, parsed, maxDrawdownPct, enabled, UserContextHolder.get()));
    }

    @GetMapping("/portfolio-circuit/list")
    public Result<List<PortfolioCircuit>> list() {
        return Result.success(portfolioCircuitService.list());
    }

    /** 策略风控看板聚合（OBS-019）。 */
    @GetMapping("/risk/overview")
    public Result<java.util.Map<String, Object>> riskOverview() {
        return Result.success(riskOverviewService.overview());
    }
}