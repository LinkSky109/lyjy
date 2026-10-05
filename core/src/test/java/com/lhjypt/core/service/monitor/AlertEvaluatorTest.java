package com.lhjypt.core.service.monitor;

import com.lhjypt.core.mapper.AlertEventMapper;
import com.lhjypt.core.mapper.AlertRuleMapper;
import com.lhjypt.core.mapper.MetricSampleMapper;
import com.lhjypt.core.model.monitor.AlertEvent;
import com.lhjypt.core.model.monitor.AlertRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AlertEvaluator 告警评估行为测试（P1）
 */
class AlertEvaluatorTest {

    private AlertRuleMapper ruleMapper;
    private MetricSampleMapper metricMapper;
    private AlertEventMapper eventMapper;
    private AlertNotificationService notificationService;
    private AlertNotifier alertNotifier;
    private AlertEvaluator evaluator;

    @BeforeEach
    void setUp() {
        ruleMapper = mock(AlertRuleMapper.class);
        metricMapper = mock(MetricSampleMapper.class);
        eventMapper = mock(AlertEventMapper.class);
        notificationService = mock(AlertNotificationService.class);
        alertNotifier = mock(AlertNotifier.class);
        evaluator = new AlertEvaluator(ruleMapper, metricMapper, eventMapper, notificationService, alertNotifier);
    }

    private AlertRule gtRule(String id, String metric, double threshold) {
        AlertRule r = new AlertRule();
        r.setRuleId(id);
        r.setName("内存告警");
        r.setMetricKey(metric);
        r.setCondition("GT");
        r.setThreshold(threshold);
        r.setWindowSec(60);
        r.setSeverity("WARN");
        r.setEnabled(true);
        r.setChannelsJson("[\"log\"]");
        return r;
    }

    @Test
    @DisplayName("指标均值超过阈值时生成 OPEN 告警事件并发通知")
    void triggerAlertWhenOverThreshold() {
        when(ruleMapper.selectEnabledRules()).thenReturn(List.of(gtRule("AR1", "system.mem", 80)));
        when(metricMapper.queryWindowAvg(eq("system.mem"), any(), any())).thenReturn(90.0);

        evaluator.evaluate();

        ArgumentCaptor<AlertEvent> captor = ArgumentCaptor.forClass(AlertEvent.class);
        verify(eventMapper).insert(captor.capture());
        AlertEvent e = captor.getValue();
        assertThat(e.getStatus()).isEqualTo("OPEN");
        assertThat(e.getRuleId()).isEqualTo("AR1");
        assertThat(e.getSeverity()).isEqualTo("WARN");
        assertThat(e.getValue()).isEqualTo("90.00");
        verify(notificationService).send(any(AlertEvent.class), any(AlertRule.class));
        // 新生成告警事件落库后触发外部渠道通知
        verify(alertNotifier).notify(any(AlertEvent.class));
    }

    @Test
    @DisplayName("指标均值未达阈值时不生成告警")
    void noAlertWhenBelowThreshold() {
        when(ruleMapper.selectEnabledRules()).thenReturn(List.of(gtRule("AR2", "system.mem", 80)));
        when(metricMapper.queryWindowAvg(eq("system.mem"), any(), any())).thenReturn(70.0);

        evaluator.evaluate();

        verify(eventMapper, never()).insert(any());
        verify(notificationService, never()).send(any(), any());
        verify(alertNotifier, never()).notify(any());
    }

    @Test
    @DisplayName("窗口内无采样数据时跳过，不生成告警")
    void skipWhenNoSampleData() {
        when(ruleMapper.selectEnabledRules()).thenReturn(List.of(gtRule("AR3", "system.mem", 80)));
        when(metricMapper.queryWindowAvg(anyString(), any(), any())).thenReturn(null);

        evaluator.evaluate();

        verify(eventMapper, never()).insert(any());
    }

    @Test
    @DisplayName("单条规则评估异常不影响其余规则继续评估")
    void oneRuleFailureDoesNotBlockOthers() {
        AlertRule bad = gtRule("AR4", "bad.metric", 80);
        AlertRule good = gtRule("AR5", "good.metric", 80);
        when(ruleMapper.selectEnabledRules()).thenReturn(List.of(bad, good));
        // bad 指标查询抛异常，good 正常触发
        when(metricMapper.queryWindowAvg(eq("bad.metric"), any(), any()))
                .thenThrow(new RuntimeException("boom"));
        when(metricMapper.queryWindowAvg(eq("good.metric"), any(), any())).thenReturn(99.0);

        evaluator.evaluate();

        ArgumentCaptor<AlertEvent> captor = ArgumentCaptor.forClass(AlertEvent.class);
        verify(eventMapper).insert(captor.capture());
        assertThat(captor.getValue().getRuleId()).isEqualTo("AR5");
    }
}
