package com.lhjypt.core.service.regulatory;

import com.lhjypt.core.common.auth.ConfirmTokenService;
import com.lhjypt.core.mapper.AmlAlertMapper;
import com.lhjypt.core.mapper.AmlRuleMapper;
import com.lhjypt.core.mapper.TradeRecordMapper;
import com.lhjypt.core.model.regulatory.AmlAlert;
import com.lhjypt.core.model.regulatory.AmlRule;
import com.lhjypt.core.model.trade.TradeRecord;
import com.lhjypt.core.model.trade.TurnoverAggregate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AmlMonitoringServiceTest {

    private AmlRuleMapper ruleMapper;
    private AmlAlertMapper alertMapper;
    private TradeRecordMapper tradeRecordMapper;
    private ConfirmTokenService confirmTokenService;
    private AmlMonitoringService service;

    @BeforeEach
    void setUp() {
        ruleMapper = mock(AmlRuleMapper.class);
        alertMapper = mock(AmlAlertMapper.class);
        tradeRecordMapper = mock(TradeRecordMapper.class);
        confirmTokenService = new ConfirmTokenService();
        service = new AmlMonitoringService(ruleMapper, alertMapper, tradeRecordMapper, confirmTokenService);
    }

    @Test
    @DisplayName("扫描：单笔大额成交生成告警并落库")
    void scanCreatesSingleAlert() {
        when(ruleMapper.selectEnabledRules())
                .thenReturn(List.of(rule("LARGE_SINGLE", "600000", 0)));
        when(tradeRecordMapper.selectLargeTrades(eq(new BigDecimal("600000")), any()))
                .thenReturn(List.of(trade("T1", "700000")));
        when(alertMapper.countOpenDedupe("LARGE_SINGLE", "ACC-1", "S-1", "rb2410")).thenReturn(0);

        List<AmlAlert> created = service.scan();

        assertThat(created).hasSize(1);
        ArgumentCaptor<AmlAlert> captor = ArgumentCaptor.forClass(AmlAlert.class);
        verify(alertMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AmlAlert.OPEN);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("700000");
    }

    @Test
    @DisplayName("扫描：已有 OPEN 告警去重，不再生成")
    void scanDedupesExistingOpen() {
        when(ruleMapper.selectEnabledRules())
                .thenReturn(List.of(rule("LARGE_SINGLE", "600000", 0)));
        when(tradeRecordMapper.selectLargeTrades(eq(new BigDecimal("600000")), any()))
                .thenReturn(List.of(trade("T1", "700000")));
        when(alertMapper.countOpenDedupe("LARGE_SINGLE", "ACC-1", "S-1", "rb2410")).thenReturn(1);

        List<AmlAlert> created = service.scan();

        assertThat(created).isEmpty();
        verify(alertMapper, never()).insert(any());
    }

    @Test
    @DisplayName("扫描：账户累计窗口成交额超阈生成告警")
    void scanCreatesCumulativeAlert() {
        when(ruleMapper.selectEnabledRules())
                .thenReturn(List.of(rule("LARGE_CUMULATIVE", "1000000", 86400)));
        TurnoverAggregate aggregate = new TurnoverAggregate();
        aggregate.setAccountId("ACC-1");
        aggregate.setTurnover(new BigDecimal("1500000"));
        when(tradeRecordMapper.selectCumulativeTurnover(any(), eq(new BigDecimal("1000000"))))
                .thenReturn(List.of(aggregate));
        when(alertMapper.countOpenDedupe("LARGE_CUMULATIVE", "ACC-1", null, null)).thenReturn(0);

        List<AmlAlert> created = service.scan();

        assertThat(created).hasSize(1);
        verify(alertMapper).insert(any(AmlAlert.class));
    }

    @Test
    @DisplayName("上报：缺少确认令牌被拒，有效令牌后 OPEN→REPORTED 留痕")
    void reportRequiresTokenAndTransitions() {
        AmlAlert alert = alert("AML-1", AmlAlert.OPEN);
        when(alertMapper.selectByAlertId("AML-1")).thenReturn(alert);

        assertThatThrownBy(() -> service.report("AML-1", "comp-1", "复核通过", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("二次确认");

        String token = confirmTokenService.issue("COMPLIANCE");
        AmlAlert reported = service.report("AML-1", "comp-1", "复核通过", token);

        assertThat(reported.getStatus()).isEqualTo(AmlAlert.REPORTED);
        assertThat(reported.getHandler()).isEqualTo("comp-1");
        verify(alertMapper).updateById(alert);
    }

    @Test
    @DisplayName("关闭：OPEN→CLOSED 并留痕")
    void closeTransitions() {
        AmlAlert alert = alert("AML-1", AmlAlert.OPEN);
        when(alertMapper.selectByAlertId("AML-1")).thenReturn(alert);

        AmlAlert closed = service.close("AML-1", "comp-1", "误报关闭");

        assertThat(closed.getStatus()).isEqualTo(AmlAlert.CLOSED);
        assertThat(closed.getHandleNote()).isEqualTo("误报关闭");
        verify(alertMapper).updateById(alert);
    }

    private static AmlRule rule(String code, String threshold, int periodSeconds) {
        AmlRule rule = new AmlRule();
        rule.setRuleCode(code);
        rule.setRuleId("R-" + code);
        rule.setName(code);
        rule.setThreshold(new BigDecimal(threshold));
        rule.setPeriodSeconds(periodSeconds);
        rule.setSeverity("WARN");
        rule.setEnabled(true);
        return rule;
    }

    private static TradeRecord trade(String tradeId, String turnover) {
        TradeRecord trade = new TradeRecord();
        trade.setTradeId(tradeId);
        trade.setAccountId("ACC-1");
        trade.setStrategyId("S-1");
        trade.setSymbol("rb2410");
        trade.setTurnover(new BigDecimal(turnover));
        trade.setTradeTime(LocalDateTime.now());
        return trade;
    }

    private static AmlAlert alert(String alertId, String status) {
        AmlAlert alert = new AmlAlert();
        alert.setAlertId(alertId);
        alert.setStatus(status);
        alert.setRuleCode("LARGE_SINGLE");
        return alert;
    }
}