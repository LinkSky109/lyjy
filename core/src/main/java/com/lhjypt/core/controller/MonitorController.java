package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.mapper.AlertEventMapper;
import com.lhjypt.core.mapper.AlertRuleMapper;
import com.lhjypt.core.mapper.MetricSampleMapper;
import com.lhjypt.core.model.monitor.AlertEvent;
import com.lhjypt.core.model.monitor.AlertRule;
import com.lhjypt.core.model.monitor.MetricSample;
import com.lhjypt.core.service.dashboard.DashboardService;
import com.lhjypt.core.service.monitor.MetricSampler;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 监控告警 API 控制器
 */
@RestController
@RequestMapping("/api/monitor")
public class MonitorController {
    
    private final MetricSampleMapper metricSampleMapper;
    private final AlertRuleMapper alertRuleMapper;
    private final AlertEventMapper alertEventMapper;
    private final MetricSampler metricSampler;
    private final DashboardService dashboardService;

    public MonitorController(MetricSampleMapper metricSampleMapper,
                             AlertRuleMapper alertRuleMapper,
                             AlertEventMapper alertEventMapper,
                             MetricSampler metricSampler,
                             DashboardService dashboardService) {
        this.metricSampleMapper = metricSampleMapper;
        this.alertRuleMapper = alertRuleMapper;
        this.alertEventMapper = alertEventMapper;
        this.metricSampler = metricSampler;
        this.dashboardService = dashboardService;
    }

    /**
     * 服务组件状态（PostgreSQL / ClickHouse / Redis / Kafka）。
     * Kafka 未注册健康组件时返回 UNKNOWN。
     */
    @GetMapping("/components")
    public Result<Map<String, Object>> components() {
        return Result.success(dashboardService.components());
    }
    
    /**
     * 查询最新指标快照
     */
    @GetMapping("/metrics/latest")
    public Result<Map<String, Object>> getLatestMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        String[] metricKeys = {
            "jvm.memory.used", "jvm.memory.max",
            "system.cpu.load", "system.cpu.count",
            "jvm.threads.count"
        };
        
        for (String key : metricKeys) {
            MetricSample sample = metricSampleMapper.queryLatest(key);
            if (sample != null) {
                metrics.put(key, sample.getValue());
            }
        }
        
        return Result.success(metrics);
    }
    
    /**
     * 查询指标历史
     */
    @GetMapping("/metrics/history")
    public Result<List<MetricSample>> getMetricsHistory(
            @RequestParam String metric,
            @RequestParam String from,
            @RequestParam String to) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        LocalDateTime fromTime = LocalDateTime.parse(from, formatter);
        LocalDateTime toTime = LocalDateTime.parse(to, formatter);
        List<MetricSample> samples = metricSampleMapper.queryHistory(metric, fromTime, toTime);
        return Result.success(samples);
    }
    
    /**
     * 创建/更新告警规则
     */
    @PostMapping("/alert-rule")
    public Result<String> saveAlertRule(@RequestBody AlertRule rule) {
        AlertRule existing = alertRuleMapper.selectByRuleId(rule.getRuleId());
        if (existing != null) {
            alertRuleMapper.updateById(rule);
            return Result.success("告警规则已更新: " + rule.getRuleId());
        } else {
            alertRuleMapper.insert(rule);
            return Result.success("告警规则已创建: " + rule.getRuleId());
        }
    }
    
    /**
     * 查询告警规则列表
     */
    @GetMapping("/alert-rule/list")
    public Result<List<AlertRule>> listAlertRules() {
        List<AlertRule> rules = alertRuleMapper.selectList(null);
        return Result.success(rules);
    }
    
    /**
     * 切换告警规则启用状态
     */
    @PostMapping("/alert-rule/{ruleId}/toggle")
    public Result<String> toggleAlertRule(@PathVariable String ruleId,
                                          @RequestParam boolean enabled) {
        int rows = alertRuleMapper.toggleEnabled(ruleId, enabled);
        if (rows == 0) {
            return Result.error("告警规则不存在: " + ruleId);
        }
        return Result.success("告警规则已" + (enabled ? "启用" : "禁用") + ": " + ruleId);
    }
    
    /**
     * 查询告警事件列表
     */
    @GetMapping("/alerts")
    public Result<List<AlertEvent>> listAlerts(@RequestParam(required = false) String status) {
        List<AlertEvent> events;
        if (status != null && !status.isEmpty()) {
            events = alertEventMapper.selectByStatus(status);
        } else {
            events = alertEventMapper.selectRecent();
        }
        return Result.success(events);
    }
    
    /**
     * 确认告警
     */
    @PostMapping("/alerts/{eventId}/ack")
    public Result<String> ackAlert(@PathVariable String eventId,
                                   @RequestParam String ackBy) {
        int rows = alertEventMapper.ackEvent(eventId, ackBy);
        if (rows == 0) {
            return Result.error("告警事件不存在或已确认: " + eventId);
        }
        return Result.success("告警已确认: " + eventId);
    }
    
    /**
     * 关闭告警
     */
    @PostMapping("/alerts/{eventId}/close")
    public Result<String> closeAlert(@PathVariable String eventId) {
        int rows = alertEventMapper.closeEvent(eventId);
        if (rows == 0) {
            return Result.error("告警事件不存在: " + eventId);
        }
        return Result.success("告警已关闭: " + eventId);
    }
    
    /**
     * 手动记录业务指标
     */
    @PostMapping("/metrics/record")
    public Result<String> recordMetric(@RequestParam String metric,
                                       @RequestParam Double value,
                                       @RequestParam(required = false) String labels) {
        metricSampler.recordMetric(metric, value, labels != null ? labels : "{}");
        return Result.success("指标已记录: " + metric);
    }
}
