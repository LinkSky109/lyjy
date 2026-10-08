package com.lhjypt.core.service.strategy;

import com.alibaba.fastjson2.JSON;
import com.lhjypt.core.common.auth.UserContext;
import com.lhjypt.core.config.DistributedLock;
import com.lhjypt.core.mapper.AlertEventMapper;
import com.lhjypt.core.mapper.PortfolioCircuitMapper;
import com.lhjypt.core.mapper.PositionMapper;
import com.lhjypt.core.mapper.StrategyInfoMapper;
import com.lhjypt.core.model.Position;
import com.lhjypt.core.model.monitor.AlertEvent;
import com.lhjypt.core.model.strategy.PortfolioCircuit;
import com.lhjypt.core.model.strategy.StrategyInfo;
import com.lhjypt.core.service.monitor.AlertNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 组合级风控（OBS-018）：多策略成员合计浮盈率峰值回撤超限 → 批量停止组内运行策略 + 告警。
 * 与 OBS-014（组合分配）衔接：分配后的组合可整体设风控。
 */
@Service
public class PortfolioCircuitService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioCircuitService.class);
    private static final UserContext SYSTEM = UserContext.of("system",
            UserContext.ROLE_ADMIN, null);

    private final PortfolioCircuitMapper portfolioCircuitMapper;
    private final StrategyInfoMapper strategyInfoMapper;
    private final PositionMapper positionMapper;
    private final StrategySecurityService strategySecurityService;
    private final DistributedLock distributedLock;
    private final AlertEventMapper alertEventMapper;
    private final AlertNotifier alertNotifier;

    public PortfolioCircuitService(PortfolioCircuitMapper portfolioCircuitMapper,
                                   StrategyInfoMapper strategyInfoMapper,
                                   PositionMapper positionMapper,
                                   StrategySecurityService strategySecurityService,
                                   DistributedLock distributedLock,
                                   AlertEventMapper alertEventMapper,
                                   AlertNotifier alertNotifier) {
        this.portfolioCircuitMapper = portfolioCircuitMapper;
        this.strategyInfoMapper = strategyInfoMapper;
        this.positionMapper = positionMapper;
        this.strategySecurityService = strategySecurityService;
        this.distributedLock = distributedLock;
        this.alertEventMapper = alertEventMapper;
        this.alertNotifier = alertNotifier;
    }

    /** 设置组合风控（成员策略逐一做归属校验）。 */
    public PortfolioCircuit setCircuit(String portfolioKey, List<String> members,
                                       BigDecimal maxDrawdownPct, boolean enabled,
                                       UserContext context) {
        if (portfolioKey == null || portfolioKey.isBlank()) {
            throw new IllegalArgumentException("portfolioKey 不能为空");
        }
        if (members == null || members.isEmpty()) {
            throw new IllegalArgumentException("成员策略不能为空");
        }
        for (String member : members) {
            strategySecurityService.requireStrategyOwnerOrAdmin(member, context);
        }
        PortfolioCircuit circuit = portfolioCircuitMapper.selectByKey(portfolioKey);
        if (circuit == null) {
            circuit = new PortfolioCircuit();
            circuit.setPortfolioKey(portfolioKey);
        }
        circuit.setMemberStrategies(JSON.toJSONString(members));
        if (maxDrawdownPct != null) {
            circuit.setMaxDrawdownPct(maxDrawdownPct);
        } else if (circuit.getMaxDrawdownPct() == null) {
            circuit.setMaxDrawdownPct(new BigDecimal("0.05"));
        }
        circuit.setEnabled(enabled);
        circuit.setTripped(false);
        circuit.setUpdateTime(LocalDateTime.now());
        if (circuit.getId() == null) {
            portfolioCircuitMapper.insert(circuit);
        } else {
            portfolioCircuitMapper.updateById(circuit);
        }
        log.info("组合风控配置: {} members={} maxDrawdown={} enabled={}",
                portfolioKey, members, circuit.getMaxDrawdownPct(), enabled);
        return circuit;
    }

    public List<PortfolioCircuit> list() {
        return portfolioCircuitMapper.selectList(null);
    }

    @Scheduled(fixedRateString = "${lhjypt.strategy.portfolio-circuit.scan-interval-ms:30000}")
    public void scan() {
        distributedLock.execute("strategy.portfolio-circuit", () -> {
            for (PortfolioCircuit circuit : portfolioCircuitMapper.selectEnabled()) {
                try {
                    check(circuit);
                } catch (Exception error) {
                    log.warn("组合风控检查异常 {}: {}", circuit.getPortfolioKey(), error.getMessage());
                }
            }
        });
    }

    private void check(PortfolioCircuit circuit) {
        List<String> members = parseMembers(circuit.getMemberStrategies());
        BigDecimal pnl = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        List<String> runningMembers = new ArrayList<>();
        for (String member : members) {
            StrategyInfo info = strategyInfoMapper.selectByStrategyId(member);
            if (info == null || !StrategyInfo.RUNNING.equals(info.getStatus())) {
                continue;
            }
            runningMembers.add(member);
            for (Position position : positionMapper.selectByStrategy(member)) {
                BigDecimal unrealized = position.getUnrealizedPnl() == null
                        ? BigDecimal.ZERO : position.getUnrealizedPnl();
                pnl = pnl.add(unrealized);
                cost = cost.add(position.getQuantity() == null || position.getAvgCost() == null
                        ? BigDecimal.ZERO
                        : position.getQuantity().multiply(position.getAvgCost()).abs());
            }
        }
        if (runningMembers.isEmpty() || cost.signum() <= 0) {
            return; // 无运行成员或无持仓成本
        }
        BigDecimal pnlRate = pnl.divide(cost, 8, RoundingMode.HALF_UP);
        BigDecimal peak = circuit.getPeakPnlRate();
        if (peak == null || pnlRate.compareTo(peak) > 0) {
            circuit.setPeakPnlRate(pnlRate);
            circuit.setUpdateTime(LocalDateTime.now());
            portfolioCircuitMapper.updateById(circuit);
            peak = pnlRate;
        }
        BigDecimal drawdown = peak.subtract(pnlRate);
        if (drawdown.compareTo(circuit.getMaxDrawdownPct()) < 0) {
            return;
        }
        log.warn("组合风控触发: {} 峰值={} 当前={} 回撤={} 阈值={} 成员={}",
                circuit.getPortfolioKey(), peak, pnlRate, drawdown,
                circuit.getMaxDrawdownPct(), runningMembers);
        for (String member : runningMembers) {
            try {
                strategySecurityService.stop(member, SYSTEM, "portfolio-circuit");
            } catch (Exception error) {
                log.warn("组合风控停止成员失败 {}: {}", member, error.getMessage());
            }
        }
        circuit.setTripped(true);
        circuit.setLastTripTime(LocalDateTime.now());
        circuit.setUpdateTime(LocalDateTime.now());
        portfolioCircuitMapper.updateById(circuit);
        notifyTrip(circuit, runningMembers, peak, pnlRate, drawdown);
    }

    private void notifyTrip(PortfolioCircuit circuit, List<String> members,
                            BigDecimal peak, BigDecimal current, BigDecimal drawdown) {
        try {
            AlertEvent event = new AlertEvent();
            event.setEventId("EVT-" + UUID.randomUUID().toString().substring(0, 8));
            event.setRuleId("portfolio.circuit");
            event.setMetricKey("portfolio:" + circuit.getPortfolioKey());
            event.setValue(String.format("%.4f", drawdown));
            event.setSeverity("CRITICAL");
            event.setMessage(String.format(
                    "[CRITICAL] 组合风控触发 %s - 峰值浮盈率 %s 当前 %s 回撤 %s ≥ 阈值 %s，已停止成员 %s",
                    circuit.getPortfolioKey(), peak, current, drawdown,
                    circuit.getMaxDrawdownPct(), members));
            event.setStatus("OPEN");
            event.setCreateTime(LocalDateTime.now());
            alertEventMapper.insert(event);
            alertNotifier.notify(event);
        } catch (Exception error) {
            log.warn("组合风控告警通知失败（不影响停止）: {}", error.getMessage());
        }
    }

    static List<String> parseMembers(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> members = JSON.parseArray(json, String.class);
            return members == null ? List.of() : members;
        } catch (Exception error) {
            return List.of();
        }
    }
}