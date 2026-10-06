package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.common.auth.UserContextHolder;
import com.lhjypt.core.model.settlement.SettlementRule;
import com.lhjypt.core.model.settlement.SettlementTask;
import com.lhjypt.core.service.settlement.SettlementTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 跨境交收任务 API（P2 模块一 §1.4）。
 */
@RestController
@RequestMapping("/api/settlement/task")
@RequiredArgsConstructor
public class SettlementTaskController {

    private final SettlementTaskService settlementTaskService;

    @PostMapping("/generate")
    public Result<Integer> generate(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        return Result.success(settlementTaskService.generateForDay(tradeDate));
    }

    @PostMapping("/process")
    public Result<Integer> process(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return Result.success(settlementTaskService.processDue(asOf));
    }

    @GetMapping("/rules")
    public Result<List<SettlementRule>> rules() {
        return Result.success(settlementTaskService.rules());
    }

    @GetMapping("/list")
    public Result<List<SettlementTask>> list(@RequestParam(required = false) String status) {
        return Result.success(settlementTaskService.list(status));
    }

    @GetMapping("/{taskId}")
    public Result<SettlementTask> detail(@PathVariable String taskId) {
        return Result.success(settlementTaskService.detail(taskId));
    }

    @PostMapping("/{taskId}/settle")
    public Result<SettlementTask> settle(@PathVariable String taskId) {
        return Result.success(settlementTaskService.settle(taskId,
                UserContextHolder.get().userId()));
    }

    @PostMapping("/{taskId}/fail")
    public Result<SettlementTask> fail(@PathVariable String taskId,
                                       @RequestParam(required = false) String reason) {
        return Result.success(settlementTaskService.fail(taskId, reason));
    }
}