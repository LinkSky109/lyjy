package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.common.auth.UserContextHolder;
import com.lhjypt.core.model.risk.RiskBudget;
import com.lhjypt.core.service.risk.RiskBudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 风险预算分配与占用监控 API（P2 模块二 §2.3）。
 */
@RestController
@RequestMapping("/api/risk/budget")
@RequiredArgsConstructor
public class RiskBudgetController {

    private final RiskBudgetService riskBudgetService;

    @GetMapping("/list")
    public Result<List<RiskBudget>> list(@RequestParam(required = false) String scopeType,
                                         @RequestParam(required = false) String scopeId) {
        return Result.success(riskBudgetService.list(scopeType, scopeId));
    }

    @PostMapping("/save")
    public Result<RiskBudget> save(@RequestBody RiskBudget budget) {
        return Result.success(riskBudgetService.save(budget,
                UserContextHolder.get().userId()));
    }

    @PostMapping("/refresh")
    public Result<Integer> refresh() {
        return Result.success(riskBudgetService.refreshAll());
    }

    @GetMapping("/{budgetId}")
    public Result<RiskBudget> detail(@PathVariable String budgetId) {
        return Result.success(riskBudgetService.detail(budgetId));
    }
}