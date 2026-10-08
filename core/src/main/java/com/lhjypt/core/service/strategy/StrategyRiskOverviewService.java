package com.lhjypt.core.service.strategy;

import com.lhjypt.core.mapper.AlertEventMapper;
import com.lhjypt.core.mapper.PortfolioCircuitMapper;
import com.lhjypt.core.mapper.StrategyCircuitMapper;
import com.lhjypt.core.mapper.StrategyInfoMapper;
import com.lhjypt.core.model.monitor.AlertEvent;
import com.lhjypt.core.model.strategy.PortfolioCircuit;
import com.lhjypt.core.model.strategy.StrategyCircuit;
import com.lhjypt.core.model.strategy.StrategyInfo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略风控看板聚合（OBS-019）：单策略熔断 / 组合风控 / 近期风控告警。
 */
@Service
public class StrategyRiskOverviewService {

    private static final List<String> RISK_RULES =
            List.of("strategy.circuit", "portfolio.circuit");

    private final StrategyInfoMapper strategyInfoMapper;
    private final StrategyCircuitMapper strategyCircuitMapper;
    private final PortfolioCircuitMapper portfolioCircuitMapper;
    private final AlertEventMapper alertEventMapper;

    public StrategyRiskOverviewService(StrategyInfoMapper strategyInfoMapper,
                                       StrategyCircuitMapper strategyCircuitMapper,
                                       PortfolioCircuitMapper portfolioCircuitMapper,
                                       AlertEventMapper alertEventMapper) {
        this.strategyInfoMapper = strategyInfoMapper;
        this.strategyCircuitMapper = strategyCircuitMapper;
        this.portfolioCircuitMapper = portfolioCircuitMapper;
        this.alertEventMapper = alertEventMapper;
    }

    public Map<String, Object> overview() {
        List<StrategyInfo> strategies = strategyInfoMapper.selectList(null);
        List<StrategyCircuit> circuits = strategyCircuitMapper.selectList(null);
        List<PortfolioCircuit> portfolios = portfolioCircuitMapper.selectList(null);
        List<AlertEvent> alerts = alertEventMapper.selectByRuleIds(RISK_RULES, 20);

        Map<String, StrategyCircuit> circuitByStrategy = new LinkedHashMap<>();
        for (StrategyCircuit circuit : circuits) {
            circuitByStrategy.put(circuit.getStrategyId(), circuit);
        }

        int running = 0;
        int tripped = 0;
        List<Map<String, Object>> strategyRows = new ArrayList<>();
        for (StrategyInfo strategy : strategies) {
            boolean isRunning = StrategyInfo.RUNNING.equals(strategy.getStatus());
            if (isRunning) {
                running++;
            }
            StrategyCircuit circuit = circuitByStrategy.get(strategy.getStrategyId());
            if (circuit != null && Boolean.TRUE.equals(circuit.getTripped())) {
                tripped++;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("strategyId", strategy.getStrategyId());
            row.put("name", strategy.getName());
            row.put("owner", strategy.getOwner());
            row.put("status", strategy.getStatus());
            row.put("circuitEnabled", circuit != null && Boolean.TRUE.equals(circuit.getEnabled()));
            row.put("maxDrawdownPct", circuit == null ? null : circuit.getMaxDrawdownPct());
            row.put("peakPnlRate", circuit == null ? null : circuit.getPeakPnlRate());
            row.put("circuitTripped", circuit != null && Boolean.TRUE.equals(circuit.getTripped()));
            row.put("lastTripTime", circuit == null ? null : circuit.getLastTripTime());
            strategyRows.add(row);
        }

        List<Map<String, Object>> portfolioRows = new ArrayList<>();
        int portfolioTripped = 0;
        for (PortfolioCircuit portfolio : portfolios) {
            if (Boolean.TRUE.equals(portfolio.getTripped())) {
                portfolioTripped++;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("portfolioKey", portfolio.getPortfolioKey());
            row.put("members", PortfolioCircuitService.parseMembers(
                    portfolio.getMemberStrategies()));
            row.put("maxDrawdownPct", portfolio.getMaxDrawdownPct());
            row.put("peakPnlRate", portfolio.getPeakPnlRate());
            row.put("enabled", portfolio.getEnabled());
            row.put("tripped", portfolio.getTripped());
            row.put("lastTripTime", portfolio.getLastTripTime());
            portfolioRows.add(row);
        }

        List<Map<String, Object>> alertRows = new ArrayList<>();
        for (AlertEvent alert : alerts) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("eventId", alert.getEventId());
            row.put("ruleId", alert.getRuleId());
            row.put("metricKey", alert.getMetricKey());
            row.put("severity", alert.getSeverity());
            row.put("status", alert.getStatus());
            row.put("message", alert.getMessage());
            row.put("createTime", alert.getCreateTime());
            alertRows.add(row);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("runningStrategies", running);
        summary.put("totalStrategies", strategies.size());
        summary.put("circuitConfigured", circuits.size());
        summary.put("circuitTripped", tripped);
        summary.put("portfolios", portfolios.size());
        summary.put("portfolioTripped", portfolioTripped);
        summary.put("recentAlerts", alerts.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("strategies", strategyRows);
        result.put("portfolios", portfolioRows);
        result.put("alerts", alertRows);
        return result;
    }
}