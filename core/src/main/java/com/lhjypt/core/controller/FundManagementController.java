package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.model.fund.CashTransaction;
import com.lhjypt.core.model.fund.FundFlow;
import com.lhjypt.core.model.fund.FundReconciliation;
import com.lhjypt.core.model.trade.AccountInfo;
import com.lhjypt.core.service.fund.FundManagementService;
import com.lhjypt.core.service.fund.ReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 资金管理API
 */
@RestController
@RequestMapping("/api/fund")
@RequiredArgsConstructor
public class FundManagementController {

    private final FundManagementService fundService;
    private final ReconciliationService reconciliationService;

    /**
     * 提交出入金申请
     */
    @PostMapping("/transaction")
    public Result<CashTransaction> apply(@RequestBody CashTransaction request) {
        return Result.success(fundService.applyCashTransaction(request));
    }

    /**
     * 审核出入金
     */
    @PutMapping("/transaction/{no}/approve")
    public Result<CashTransaction> approve(@PathVariable String no,
                                           @RequestParam String approver,
                                           @RequestParam boolean approved) {
        return Result.success(fundService.approve(no, approver, approved));
    }

    /**
     * 资金账户分页（P1-b）。
     */
    @GetMapping("/accounts")
    public Result<java.util.Map<String, Object>> accounts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(fundService.pageAccounts(page, size));
    }

    /**
     * 查询资金流水：不传 page 时保持旧列表契约；传 page 返回分页结构。
     */
    @GetMapping("/flows")
    public Result<?> getFlows(@RequestParam String accountId,
                              @RequestParam LocalDateTime start,
                              @RequestParam LocalDateTime end,
                              @RequestParam(required = false) String bizType,
                              @RequestParam(required = false) Integer page,
                              @RequestParam(defaultValue = "20") int size) {
        if (page != null) {
            return Result.success(fundService.pageFlows(accountId, start, end, bizType, page, size));
        }
        return Result.success(fundService.getFlows(accountId, start, end));
    }

    /**
     * 执行资金对账
     */
    @PostMapping("/reconcile")
    public Result<FundReconciliation> reconcile(@RequestParam String accountId,
                                                @RequestBody AccountInfo gatewayAccount) {
        return Result.success(reconciliationService.reconcile(accountId, gatewayAccount));
    }
}
