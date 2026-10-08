package com.lhjypt.core.service.strategy;

import com.lhjypt.core.common.auth.UserContext;
import com.lhjypt.core.config.DistributedLock;
import com.lhjypt.core.mapper.AlertEventMapper;
import com.lhjypt.core.mapper.PositionMapper;
import com.lhjypt.core.mapper.StrategyCircuitMapper;
import com.lhjypt.core.mapper.StrategyInfoMapper;
import com.lhjypt.core.model.Position;
import com.lhjypt.core.model.monitor.AlertEvent;
import com.lhjypt.core.model.strategy.StrategyCircuit;
import com.lhjypt.core.model.strategy.StrategyInfo;
import com.lhjypt.core.service.monitor.AlertNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 策略回撤熔断（OBS-016）：跟踪运行中策略的持仓浮盈率峰值，
 * 回撤超过阈值时自动停止策略（联动优化参数落档后的实盘风控）。
 */
@Service
public class StrategyCircuitBreakerService {

    private static final Logger log = LoggerFactory.getLogger(StrategyCircuitBreakerService.class);

    private final StrategyCircuitMapper circuitMapper;
    private final StrategyInfoMapper strategyInfoMapper;
    private final PositionMapper positionMapper;
    private final StrategySecurityService strategySecurityService;
    private final DistributedLock distributedLock;
    private final AlertEventMapper alertEventMapper;
    private final AlertNotifier alertNotifier;

    public StrategyCircuitBreakerService(StrategyCircuitMapper circuitMapper,
                                         StrategyInfoMapper strategyInfoMapper,
                                         PositionMapper positionMapper,
                                         StrategySecurityService strategySecurityService,
                                         DistributedLock distributedLock,
                                         AlertEventMapper alertEventMapper,
                                         AlertNotifier alertNotifier) {
        this.circuitMapper = circuitMapper;
        this.strategyInfoMapper = strategyInfoMapper;
        this.positionMapper = positionMapper;
        this.strategySecurityService = strategySecurityService;
        this.distributedLock = distributedLock;
        this.alertEventMapper = alertEventMapper;
        this.alertNotifier = alertNotifier;
    }

    /** 设置/更新熔断阈值（归属校验：非 ADMIN 仅本人策略）。 */
    public StrategyCircuit setCircuit(String strategyId, BigDecimal maxDrawdownPct,
                                      boolean enabled, UserContext context) {
        strategySecurityService.requireStrategyOwnerOrAdmin(strategyId, context);
        StrategyCircuit circuit = circuitMapper.selectByStrategyId(strategyId);
        if (circuit == null) {
            circuit = new StrategyCircuit();
            circuit.setStrategyId(strategyId);
            circuit.setPeakPnlRate(null);
        }
        if (maxDrawdownPct != null) {
            circuit.setMaxDrawdownPct(maxDrawdownPct);
        } else if (circuit.getMaxDrawdownPct() == null) {
            circuit.setMaxDrawdownPct(new BigDecimal("0.05"));
        }
        circuit.setEnabled(enabled);
        circuit.setTripped(false); // 重新配置即复位
        circuit.setUpdateTime(LocalDateTime.now());
        if (circuit.getId() == null) {
            circuitMapper.insert(circuit);
        } else {
            circuitMapper.updateById(circuit);
        }
        log.info("策略熔断配置: {} maxDrawdown={} enabled={}",
                strategyId, circuit.getMaxDrawdownPct(), enabled);
        return circuit;
    }

    public List<StrategyCircuit> list() {
        return circuitMapper.selectList(null);
    }

    @Scheduled(fixedRateString = "${lhjypt.strategy.circuit.scan-interval-ms:30000}")
    public void scan() {
        distributedLock.execute("strategy.circuit", () -> {
            for (StrategyCircuit circuit : circuitMapper.selectEnabled()) {
                try {
                    check(circuit);
                } catch (Exception error) {
                    log.warn("策略熔断检查异常 {}: {}", circuit.getStrategyId(), error.getMessage());
                }
            }
        });
    }

    private void check(StrategyCircuit circuit) {
        StrategyInfo strategy = strategyInfoMapper.selectByStrategyId(circuit.getStrategyId());
        if (strategy == null || !StrategyInfo.RUNNING.equals(strategy.getStatus())) {
            return; // 未运行无需熔断
        }
        List<Position> positions = positionMapper.selectByStrategy(circuit.getStrategyId());
        BigDecimal pnl = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (Position position : positions) {
            BigDecimal unrealized = position.getUnrealizedPnl() == null
                    ? BigDecimal.ZERO : position.getUnrealizedPnl();
            BigDecimal positionCost = position.getQuantity() == null || position.getAvgCost() == null
                    ? BigDecimal.ZERO
                    : position.getQuantity().multiply(position.getAvgCost()).abs();
            pnl = pnl.add(unrealized);
            cost = cost.add(positionCost);
        }
        if (cost.signum() <= 0) {
            return; // 无持仓成本（未建仓），不评估回撤
        }
        BigDecimal pnlRate = pnl.divide(cost, 8, RoundingMode.HALF_UP);
        BigDecimal peak = circuit.getPeakPnlRate();
        if (peak == null || pnlRate.compareTo(peak) > 0) {
            circuit.setPeakPnlRate(pnlRate);
            circuit.setUpdateTime(LocalDateTime.now());
            circuitMapper.updateById(circuit);
            peak = pnlRate;
        }
        BigDecimal drawdown = peak.subtract(pnlRate);
        if (drawdown.compareTo(circuit.getMaxDrawdownPct()) >= 0) {
            log.warn("策略回撤熔断触发: {} 峰值浮盈率={} 当前={} 回撤={} 阈值={}",
                    circuit.getStrategyId(), peak, pnlRate, drawdown, circuit.getMaxDrawdownPct());
            try {
                strategySecurityService.stop(circuit.getStrategyId(), SYSTEM, "circuit-breaker");
            } catch (Exception error) {
                log.warn("熔断停止策略失败 {}: {}", circuit.getStrategyId(), error.getMessage());
            }
            circuit.setTripped(true);
            circuit.setLastTripTime(LocalDateTime.now());
            circuit.setUpdateTime(LocalDateTime.now());
            circuitMapper.updateById(circuit);
            notifyTrip(circuit, peak, pnlRate, drawdown);
        }
    }

    /** 熔断告警：落库（ruleId=strategy.circuit）+ 外部渠道通知（失败不影响主流程）。 */
    private void notifyTrip(StrategyCircuit circuit, BigDecimal peak,
                            BigDecimal current, BigDecimal drawdown) {
        try {
            AlertEvent event = new AlertEvent();
            event.setEventId("EVT-" + UUID.randomUUID().toString().substring(0, 8));
            event.setRuleId("strategy.circuit");
            event.setMetricKey("strategy:" + circuit.getStrategyId());
            event.setValue(String.format("%.4f", drawdown));
            event.setSeverity("CRITICAL");
            event.setMessage(String.format(
                    "[CRITICAL] 策略回撤熔断 %s - 峰值浮盈率 %s 当前 %s 回撤 %s ≥ 阈值 %s，已自动停止策略",
                    circuit.getStrategyId(), peak, current, drawdown, circuit.getMaxDrawdownPct()));
            event.setStatus("OPEN");
            event.setCreateTime(LocalDateTime.now());
            alertEventMapper.insert(event);
            alertNotifier.notify(event);
        } catch (Exception error) {
            log.warn("熔断告警通知失败（不影响停止）: {}", error.getMessage());
        }
    }

    private static final UserContext SYSTEM = UserContext.of("system",
            UserContext.ROLE_ADMIN, null);
}