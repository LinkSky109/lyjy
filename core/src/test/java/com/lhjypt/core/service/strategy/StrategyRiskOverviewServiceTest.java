package com.lhjypt.core.service.strategy;

import com.lhjypt.core.mapper.AlertEventMapper;
import com.lhjypt.core.mapper.PortfolioCircuitMapper;
import com.lhjypt.core.mapper.StrategyCircuitMapper;
import com.lhjypt.core.mapper.StrategyInfoMapper;
import com.lhjypt.core.model.monitor.AlertEvent;
import com.lhjypt.core.model.strategy.PortfolioCircuit;
import com.lhjypt.core.model.strategy.StrategyCircuit;
import com.lhjypt.core.model.strategy.StrategyInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StrategyRiskOverviewServiceTest {

    private StrategyInfoMapper strategyInfoMapper;
    private StrategyCircuitMapper strategyCircuitMapper;
    private PortfolioCircuitMapper portfolioCircuitMapper;
    private AlertEventMapper alertEventMapper;
    private StrategyRiskOverviewService service;

    @BeforeEach
    void setUp() {
        strategyInfoMapper = mock(StrategyInfoMapper.class);
        strategyCircuitMapper = mock(StrategyCircuitMapper.class);
        portfolioCircuitMapper = mock(PortfolioCircuitMapper.class);
        alertEventMapper = mock(AlertEventMapper.class);
        service = new StrategyRiskOverviewService(strategyInfoMapper,
                strategyCircuitMapper, portfolioCircuitMapper, alertEventMapper);
    }

    @Test
    @DisplayName("看板聚合：运行数/熔断数/组合数/告警列表")
    void aggregatesOverview() {
        StrategyInfo running = new StrategyInfo();
        running.setStrategyId("S1");
        running.setName("策略一");
        running.setStatus(StrategyInfo.RUNNING);
        StrategyInfo offline = new StrategyInfo();
        offline.setStrategyId("S2");
        offline.setStatus(StrategyInfo.OFFLINE);
        when(strategyInfoMapper.selectList(any())).thenReturn(List.of(running, offline));

        StrategyCircuit circuit = new StrategyCircuit();
        circuit.setStrategyId("S1");
        circuit.setEnabled(true);
        circuit.setMaxDrawdownPct(new BigDecimal("0.05"));
        circuit.setPeakPnlRate(new BigDecimal("0.02"));
        circuit.setTripped(true);
        when(strategyCircuitMapper.selectList(any())).thenReturn(List.of(circuit));

        PortfolioCircuit portfolio = new PortfolioCircuit();
        portfolio.setPortfolioKey("PF-1");
        portfolio.setMemberStrategies("[\"S1\",\"S2\"]");
        portfolio.setTripped(false);
        portfolio.setEnabled(true);
        when(portfolioCircuitMapper.selectList(any())).thenReturn(List.of(portfolio));

        AlertEvent alert = new AlertEvent();
        alert.setEventId("EVT-1");
        alert.setRuleId("strategy.circuit");
        alert.setSeverity("CRITICAL");
        when(alertEventMapper.selectByRuleIds(any(), anyInt())).thenReturn(List.of(alert));

        Map<String, Object> result = service.overview();

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        assertThat(summary.get("runningStrategies")).isEqualTo(1);
        assertThat(summary.get("totalStrategies")).isEqualTo(2);
        assertThat(summary.get("circuitConfigured")).isEqualTo(1);
        assertThat(summary.get("circuitTripped")).isEqualTo(1);
        assertThat(summary.get("portfolios")).isEqualTo(1);
        assertThat(summary.get("recentAlerts")).isEqualTo(1);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> strategies = (List<Map<String, Object>>) result.get("strategies");
        assertThat(strategies).hasSize(2);
        assertThat(strategies.get(0).get("circuitTripped")).isEqualTo(true);
        assertThat(strategies.get(0).get("maxDrawdownPct")).isNotNull();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> portfolios = (List<Map<String, Object>>) result.get("portfolios");
        assertThat(portfolios.get(0).get("members")).isEqualTo(List.of("S1", "S2"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> alerts = (List<Map<String, Object>>) result.get("alerts");
        assertThat(alerts.get(0).get("ruleId")).isEqualTo("strategy.circuit");
    }
}