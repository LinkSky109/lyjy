package com.lhjypt.core.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lhjypt.core.common.Result;
import com.lhjypt.core.common.auth.ConfirmTokenService;
import com.lhjypt.core.mapper.AbnormalTradeAlertMapper;
import com.lhjypt.core.model.surveillance.AbnormalTradeAlert;
import com.lhjypt.core.service.surveillance.AbnormalTradeMonitor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 异常交易监控 API（P1，TASK-P1-003）
 * 确认上报/关闭属敏感处置，需一次性确认令牌（PRD §3.5）。
 */
@RestController
@RequestMapping("/api/surveillance/abnormal")
@RequiredArgsConstructor
public class SurveillanceController {

    private final AbnormalTradeMonitor monitor;
    private final AbnormalTradeAlertMapper alertMapper;
    private final ConfirmTokenService confirmTokenService;

    /**
     * 告警列表（可按状态/账户过滤）
     */
    @GetMapping("/list")
    public Result<List<AbnormalTradeAlert>> list(@RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String accountId) {
        QueryWrapper<AbnormalTradeAlert> qw = new QueryWrapper<>();
        if (status != null && !status.isBlank()) qw.eq("status", status);
        if (accountId != null && !accountId.isBlank()) qw.eq("account_id", accountId);
        qw.orderByDesc("create_time").last("LIMIT 200");
        return Result.success(alertMapper.selectList(qw));
    }

    /**
     * 告警详情
     */
    @GetMapping("/{alertId}")
    public Result<AbnormalTradeAlert> detail(@PathVariable String alertId) {
        AbnormalTradeAlert alert = alertMapper.selectByAlertId(alertId);
        return alert == null ? Result.error("告警不存在: " + alertId) : Result.success(alert);
    }

    /**
     * 确认并上报（敏感操作，需确认令牌）
     */
    @PostMapping("/{alertId}/confirm")
    public Result<AbnormalTradeAlert> confirm(@PathVariable String alertId,
                                              @RequestParam String handler,
                                              @RequestParam(required = false) String note,
                                              @RequestHeader(value = "X-Confirm-Token", required = false) String confirmToken) {
        if (!confirmTokenService.validate(confirmToken)) {
            return Result.error(409, "缺少或无效的二次确认令牌，请先调用 /api/auth/confirm-token 获取");
        }
        AbnormalTradeAlert alert = alertMapper.selectByAlertId(alertId);
        if (alert == null) return Result.error("告警不存在: " + alertId);
        if (!"OPEN".equals(alert.getStatus())) {
            return Result.error(409, "告警已处置，请勿重复确认: " + alertId);
        }
        alert.setStatus("CONFIRMED");
        alert.setHandler(handler);
        alert.setHandleNote((note == null ? "" : note) + "｜已确认上报");
        alert.setUpdateTime(LocalDateTime.now());
        alertMapper.updateById(alert);
        return Result.success(alert);
    }

    /**
     * 关闭告警（误报处置，需确认令牌）
     */
    @PostMapping("/{alertId}/close")
    public Result<AbnormalTradeAlert> close(@PathVariable String alertId,
                                            @RequestParam String handler,
                                            @RequestParam(required = false) String note,
                                            @RequestHeader(value = "X-Confirm-Token", required = false) String confirmToken) {
        if (!confirmTokenService.validate(confirmToken)) {
            return Result.error(409, "缺少或无效的二次确认令牌，请先调用 /api/auth/confirm-token 获取");
        }
        AbnormalTradeAlert alert = alertMapper.selectByAlertId(alertId);
        if (alert == null) return Result.error("告警不存在: " + alertId);
        alert.setStatus("CLOSED");
        alert.setHandler(handler);
        alert.setHandleNote((note == null ? "" : note) + "｜已关闭");
        alert.setUpdateTime(LocalDateTime.now());
        alertMapper.updateById(alert);
        return Result.success(alert);
    }

    /**
     * 规则阈值查看/调整
     */
    @GetMapping("/rules")
    public Result<Map<String, Double>> rules() {
        return Result.success(monitor.getThresholds());
    }

    @PostMapping("/rules/{ruleCode}")
    public Result<Map<String, Double>> updateRule(@PathVariable String ruleCode,
                                                  @RequestParam double value,
                                                  @RequestHeader(value = "X-Confirm-Token", required = false) String confirmToken) {
        if (!confirmTokenService.validate(confirmToken)) {
            return Result.error(409, "缺少或无效的二次确认令牌，请先调用 /api/auth/confirm-token 获取");
        }
        try {
            monitor.updateThreshold(ruleCode, value);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
        return Result.success(monitor.getThresholds());
    }

    /**
     * 手动触发一次扫描（调试/冒烟用）
     */
    @PostMapping("/scan")
    public Result<List<AbnormalTradeAlert>> scan(@RequestParam(defaultValue = "300") int windowSec) {
        return Result.success(monitor.scanWindow(windowSec));
    }
}
