package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.common.auth.ConfirmTokenService;
import com.lhjypt.core.model.settlement.SettlementBatch;
import com.lhjypt.core.model.settlement.SettlementDiffItem;
import com.lhjypt.core.service.settlement.DailySettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 日终清算与对账 API。
 */
@RestController
@RequestMapping("/api/settlement")
@RequiredArgsConstructor
public class SettlementController {

    private final DailySettlementService settlementService;
    private final ConfirmTokenService confirmTokenService;

    @PostMapping("/run")
    public Result<SettlementBatch> run(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(defaultValue = "false") boolean force,
            @RequestHeader(value = "X-Confirm-Token", required = false)
            String confirmToken) {
        if (force && !confirmTokenService.validate(confirmToken)) {
            return Result.error(409, "重跑清算需要有效的二次确认令牌");
        }
        return Result.success(settlementService.run(tradeDate, force));
    }

    @GetMapping("/batches")
    public Result<List<SettlementBatch>> batches(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        return Result.success(settlementService.listBatches(tradeDate));
    }

    @GetMapping("/batch/{batchId}")
    public Result<SettlementBatch> batch(@PathVariable String batchId) {
        return Result.success(settlementService.getBatch(batchId));
    }

    @GetMapping("/batch/{batchId}/diffs")
    public Result<List<SettlementDiffItem>> diffs(@PathVariable String batchId) {
        return Result.success(settlementService.getDiffs(batchId));
    }

    @PostMapping("/diff/{diffId}/adjust")
    public Result<SettlementDiffItem> adjust(
            @PathVariable String diffId,
            @RequestParam String adjustBy,
            @RequestParam(required = false) String note) {
        return Result.success(settlementService.adjustDiff(
                diffId, adjustBy, note == null ? "" : note));
    }

    @PostMapping("/batch/{batchId}/close")
    public Result<SettlementBatch> close(
            @PathVariable String batchId,
            @RequestHeader(value = "X-Confirm-Token", required = false)
            String confirmToken) {
        if (!confirmTokenService.validate(confirmToken)) {
            return Result.error(409, "关闭清算批次需要有效的二次确认令牌");
        }
        return Result.success(settlementService.closeBatch(batchId));
    }

    @GetMapping("/report/{batchId}")
    public Result<Map<String, Object>> report(
            @PathVariable String batchId,
            @RequestParam(defaultValue = "pnl") String type) {
        return Result.success(settlementService.report(batchId, type));
    }
}
