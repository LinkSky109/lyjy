package com.lhjypt.core.service.monitor;

import com.lhjypt.core.mapper.AlertEventMapper;
import com.lhjypt.core.mapper.AlertRuleMapper;
import com.lhjypt.core.mapper.MetricSampleMapper;
import com.lhjypt.core.model.monitor.AlertEvent;
import com.lhjypt.core.model.monitor.AlertRule;
import com.lhjypt.core.model.monitor.MetricSample;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 告警评估服务
 * 定时评估告警规则并生成告警事件
 */
@Service
public class AlertEvaluator {
    
    private final AlertRuleMapper alertRuleMapper;
    private final MetricSampleMapper metricSampleMapper;
    private final AlertEventMapper alertEventMapper;
    private final AlertNotificationService notificationService;
    
    public AlertEvaluator(AlertRuleMapper alertRuleMapper,
                          MetricSampleMapper metricSampleMapper,
                          AlertEventMapper alertEventMapper,
                          AlertNotificationService notificationService) {
        this.alertRuleMapper = alertRuleMapper;
        this.metricSampleMapper = metricSampleMapper;
        this.alertEventMapper = alertEventMapper;
        this.notificationService = notificationService;
    }
    
    /**
     * 定时评估告警规则（默认每15秒）
     */
    @Scheduled(fixedRateString = "${lhjypt.monitoring.evaluate-interval:15000}")
    public void evaluate() {
        List<AlertRule> rules = alertRuleMapper.selectEnabledRules();
        LocalDateTime now = LocalDateTime.now();
        
        for (AlertRule rule : rules) {
            try {
                evaluateRule(rule, now);
            } catch (Exception e) {
                // 单个规则评估失败不影响其他规则
            }
        }
    }
    
    /**
     * 评估单个告警规则
     */
    private void evaluateRule(AlertRule rule, LocalDateTime now) {
        int windowSec = rule.getWindowSec() != null ? rule.getWindowSec() : 60;
        LocalDateTime from = now.minusSeconds(windowSec);
        LocalDateTime to = now;
        
        // 计算窗口内指标平均值
        Double windowAvg = metricSampleMapper.queryWindowAvg(rule.getMetricKey(), from, to);
        if (windowAvg == null) {
            return; // 无数据不触发告警
        }
        
        // 获取阈值
        Double thresholdVal = rule.getThreshold();
        if (thresholdVal == null) {
            return; // 阈值未设置跳过
        }
        double threshold = thresholdVal;
        
        // 评估条件
        boolean triggered = evaluateCondition(rule.getCondition(), windowAvg, threshold);
        if (!triggered) {
            return;
        }
        
        // 生成告警事件
        AlertEvent event = new AlertEvent();
        event.setEventId("EVT-" + UUID.randomUUID().toString().substring(0, 8));
        event.setRuleId(rule.getRuleId());
        event.setMetricKey(rule.getMetricKey());
        event.setValue(String.format("%.2f", windowAvg));
        event.setSeverity(rule.getSeverity());
        event.setMessage(String.format("[%s] %s - 当前值: %.2f %s 阈值: %s",
                rule.getSeverity(), rule.getName(), windowAvg, rule.getCondition(), rule.getThreshold()));
        event.setStatus("OPEN");
        event.setCreateTime(now);
        
        alertEventMapper.insert(event);
        
        // 发送通知
        notificationService.send(event, rule);
    }
    
    /**
     * 评估条件表达式
     */
    private boolean evaluateCondition(String condition, double value, double threshold) {
        return switch (condition.toUpperCase()) {
            case "GT" -> value > threshold;
            case "LT" -> value < threshold;
            case "GTE" -> value >= threshold;
            case "LTE" -> value <= threshold;
            case "EQ" -> Math.abs(value - threshold) < 0.001;
            default -> false;
        };
    }
}
