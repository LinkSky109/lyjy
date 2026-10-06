package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.common.auth.ConfirmTokenService;
import com.lhjypt.core.common.auth.UserContextHolder;
import com.lhjypt.core.model.regulatory.AmlAlert;
import com.lhjypt.core.model.regulatory.AmlRule;
import com.lhjypt.core.model.regulatory.RegulatoryReportBatch;
import com.lhjypt.core.service.regulatory.AmlMonitoringService;
import com.lhjypt.core.service.regulatory.RegulatoryReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 监管报送与 AML API（P2 模块四）。
 * 写权限 COMPLIANCE/ADMIN；报送提交与 AML 上报为敏感操作需一次性确认令牌。
 */
@RestController
@RequestMapping("/api/regulatory")
@RequiredArgsConstructor
public class RegulatoryController {

    private final RegulatoryReportService reportService;
    private final AmlMonitoringService amlService;
    private final ConfirmTokenService confirmTokenService;

    @PostMapping("/report/run")
    public Result<RegulatoryReportBatch> runReport(
            @RequestParam String reportType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate) {
        return Result.success(reportService.run(reportType, reportDate,
                UserContextHolder.get().userId()));
    }

    @GetMapping("/report/list")
    public Result<List<RegulatoryReportBatch>> listReports() {
        return Result.success(reportService.list());
    }

    @GetMapping("/report/{batchId}")
    public Result<Map<String, Object>> reportDetail(@PathVariable String batchId) {
        return Result.success(reportService.detail(batchId));
    }

    @PostMapping("/report/{batchId}/submit")
    public Result<RegulatoryReportBatch> submitReport(
            @PathVariable String batchId,
            @RequestHeader(value = "X-Confirm-Token", required = false) String confirmToken) {
        if (!confirmTokenService.validate(confirmToken)) {
            return Result.error(409, "报送批次需要有效的二次确认令牌");
        }
        return Result.success(reportService.submit(batchId, UserContextHolder.get().userId()));
    }

    @GetMapping("/aml/rules")
    public Result<List<AmlRule>> listRules() {
        return Result.success(amlService.allRules());
    }

    @PostMapping("/aml/rule")
    public Result<AmlRule> saveRule(@RequestBody AmlRule rule) {
        return Result.success(amlService.saveRule(rule));
    }

    @PostMapping("/aml/scan")
    public Result<List<AmlAlert>> scan() {
        return Result.success(amlService.scan());
    }

    @GetMapping("/aml/alerts")
    public Result<List<AmlAlert>> listAlerts(@RequestParam(required = false) String status) {
        return Result.success(amlService.alerts(status));
    }

    @GetMapping("/aml/alert/{alertId}")
    public Result<AmlAlert> alertDetail(@PathVariable String alertId) {
        return Result.success(amlService.alert(alertId));
    }

    @PostMapping("/aml/alert/{alertId}/report")
    public Result<AmlAlert> reportAlert(
            @PathVariable String alertId,
            @RequestParam(required = false) String note,
            @RequestHeader(value = "X-Confirm-Token", required = false) String confirmToken) {
        return Result.success(amlService.report(alertId,
                UserContextHolder.get().userId(), note, confirmToken));
    }

    @PostMapping("/aml/alert/{alertId}/close")
    public Result<AmlAlert> closeAlert(@PathVariable String alertId,
                                       @RequestParam(required = false) String note) {
        return Result.success(amlService.close(alertId,
                UserContextHolder.get().userId(), note));
    }
}