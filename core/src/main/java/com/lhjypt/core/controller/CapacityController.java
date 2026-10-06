package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.config.CapacityProperties;
import com.lhjypt.core.model.capacity.CapacitySnapshot;
import com.lhjypt.core.service.capacity.CapacityService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 容量管理与自动伸缩 API（P2 模块五）。
 */
@RestController
@RequestMapping("/api/capacity")
@RequiredArgsConstructor
public class CapacityController {

    private final CapacityService capacityService;
    private final CapacityProperties capacityProperties;

    @PostMapping("/refresh")
    public Result<CapacitySnapshot> refresh() {
        return Result.success(capacityService.collect());
    }

    @GetMapping("/latest")
    public Result<CapacitySnapshot> latest() {
        return Result.success(capacityService.latest());
    }

    @GetMapping("/history")
    public Result<List<CapacitySnapshot>> history(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return Result.success(capacityService.history(from, to));
    }

    @GetMapping("/autoscale")
    public Result<Map<String, Object>> autoscaleStatus() {
        return Result.success(Map.of(
                "enabled", capacityProperties.isAutoscaleEnabled(),
                "provider", capacityProperties.getAutoscaleProvider(),
                "warnPct", capacityProperties.getWarnPct(),
                "scalePct", capacityProperties.getScalePct(),
                "desiredMin", capacityProperties.getDesiredMin(),
                "desiredMax", capacityProperties.getDesiredMax()));
    }
}