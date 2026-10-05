package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.model.trade.PositionReconciliation;
import com.lhjypt.core.service.trade.PositionReconciliationService;
import com.lhjypt.core.service.trade.TradeRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 持仓对账与订单恢复接口。
 */
@RestController
@RequestMapping("/api/trade")
@RequiredArgsConstructor
public class TradeReconciliationController {

    private final PositionReconciliationService reconciliationService;
    private final TradeRecoveryService recoveryService;

    @PostMapping("/reconcile/positions")
    public Result<List<PositionReconciliation>> reconcilePositions(
            @RequestParam String accountId,
            @RequestParam(required = false) String strategyId) {
        return Result.success(reconciliationService.reconcile(accountId, strategyId));
    }

    @GetMapping("/reconcile/positions/recent")
    public Result<List<PositionReconciliation>> recent(
            @RequestParam(required = false) String accountId,
            @RequestParam(defaultValue = "100") int limit) {
        return Result.success(reconciliationService.recent(accountId, limit));
    }

    @PostMapping("/recovery/active-orders")
    public Result<Map<String, Object>> recoverActiveOrders() {
        int recovered = recoveryService.recoverActiveOrders();
        return Result.success(Map.of("recovered", recovered));
    }
}
