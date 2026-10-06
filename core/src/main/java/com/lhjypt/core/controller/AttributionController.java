package com.lhjypt.core.controller;

import com.alibaba.fastjson2.JSON;
import com.lhjypt.core.common.Result;
import com.lhjypt.core.common.auth.UserContextHolder;
import com.lhjypt.core.model.attribution.AttributionReport;
import com.lhjypt.core.model.attribution.BrinsonRequest;
import com.lhjypt.core.model.attribution.ExecutionRequest;
import com.lhjypt.core.model.attribution.FactorRequest;
import com.lhjypt.core.service.attribution.AttributionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 绩效归因 API（P2 模块二 §2.5）。
 */
@RestController
@RequestMapping("/api/attribution")
@RequiredArgsConstructor
public class AttributionController {

    private final AttributionService attributionService;

    @PostMapping("/brinson")
    public Result<Map<String, Object>> brinson(@RequestBody BrinsonRequest request) {
        return Result.success(persist(AttributionReport.TYPE_BRINSON, request.getScopeType(),
                request.getScopeId(), request.getPeriodStart(), request.getPeriodEnd(),
                attributionService.brinson(request)));
    }

    @PostMapping("/factor")
    public Result<Map<String, Object>> factor(@RequestBody FactorRequest request) {
        return Result.success(persist(AttributionReport.TYPE_FACTOR, request.getScopeType(),
                request.getScopeId(), request.getPeriodStart(), request.getPeriodEnd(),
                attributionService.factor(request)));
    }

    @PostMapping("/execution")
    public Result<Map<String, Object>> execution(@RequestBody ExecutionRequest request) {
        return Result.success(persist(AttributionReport.TYPE_EXECUTION, request.getScopeType(),
                request.getScopeId(), request.getPeriodStart(), request.getPeriodEnd(),
                attributionService.execution(request)));
    }

    @GetMapping("/report/list")
    public Result<List<AttributionReport>> reportList() {
        return Result.success(attributionService.reportList());
    }

    @GetMapping("/report/{reportId}")
    public Result<AttributionReport> reportDetail(@PathVariable String reportId) {
        return Result.success(attributionService.reportDetail(reportId));
    }

    private Map<String, Object> persist(String type, String scopeType, String scopeId,
                                        java.time.LocalDate start, java.time.LocalDate end,
                                        Map<String, Object> result) {
        AttributionReport report = new AttributionReport();
        report.setAttrType(type);
        report.setScopeType(scopeType);
        report.setScopeId(scopeId);
        report.setPeriodStart(start);
        report.setPeriodEnd(end);
        report.setResultJson(JSON.toJSONString(result));
        AttributionReport saved = attributionService.save(report, UserContextHolder.get().userId());
        result.put("reportId", saved.getReportId());
        return result;
    }
}