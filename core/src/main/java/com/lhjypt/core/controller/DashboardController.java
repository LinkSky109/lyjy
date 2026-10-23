package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.service.dashboard.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 仪表盘只读聚合接口。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.success(dashboardService.summary());
    }

    @GetMapping("/timeline")
    public Result<Map<String, Object>> timeline(
            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(dashboardService.timeline(limit));
    }
}
