package com.lhjypt.core.service.strategy;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioCircuitServiceTest {

    private PortfolioCircuitMapper portfolioCircuitMapper;
    private StrategyInfoMapper strategyInfoMapper;
    private PositionMapper positionMapper;
    private StrategySecurityService securityService;
    private AlertEventMapper alertEventMapper;
    private AlertNotifier alertNotifier;
    private PortfolioCircuitService service;

    @BeforeEach
    void setUp() {
        portfolioCircuitMapper = mock(PortfolioCircuitMapper.class);
        strategyInfoMapper = mock(StrategyInfoMapper.class);
        positionMapper = mock(PositionMapper.class);
        securityService = mock(StrategySecurityService.class);
        alertEventMapper = mock(AlertEventMapper.class);
        alertNotifier = mock(AlertNotifier.class);
        DistributedLock lock = mock(DistributedLock.class);
        doAnswer(inv -> {
            inv.getArgument(1, Runnable.class).run();
            return true;
        }).when(lock).execute(anyString(), any(Runnable.class));
        service = new PortfolioCircuitService(portfolioCircuitMapper, strategyInfoMapper,
                positionMapper, securityService, lock, alertEventMapper, alertNotifier);
    }

    private static PortfolioCircuit circuit(double maxDrawdown, String peak) {
        PortfolioCircuit circuit = new PortfolioCircuit();
        circuit.setId(1L);
        circuit.setPortfolioKey("PF-A");
        circuit.setMemberStrategies("[\"S1\",\"S2\"]");
        circuit.setMaxDrawdownPct(BigDecimal.valueOf(maxDrawdown));
        circuit.setEnabled(true);
        circuit.setPeakPnlRate(peak == null ? null : new BigDecimal(peak));
        circuit.setTripped(false);
        return circuit;
    }

    private static StrategyInfo running(String id) {
        StrategyInfo info = new StrategyInfo();
        info.setStrategyId(id);
        info.setStatus(StrategyInfo.RUNNING);
        return info;
    }

    private static Position position(double qty, double avgCost, double pnl) {
        Position position = new Position();
        position.setQuantity(BigDecimal.valueOf(qty));
        position.setAvgCost(BigDecimal.valueOf(avgCost));
        position.setUnrealizedPnl(BigDecimal.valueOf(pnl));
        return position;
    }

    @Test
    @DisplayName("组合回撤超阈值 → 批量停止运行中成员 + 告警")
    void tripsAndStopsAllRunningMembers() {
        when(portfolioCircuitMapper.selectEnabled()).thenReturn(List.of(
                circuit(0.02, "0.05")));
        when(strategyInfoMapper.selectByStrategyId("S1")).thenReturn(running("S1"));
        when(strategyInfoMapper.selectByStrategyId("S2")).thenReturn(running("S2"));
        // 合计浮亏率 ≈ (100 - 300)/(10000+10000) = -0.01 → 回撤 0.06 ≥ 0.02
        when(positionMapper.selectByStrategy("S1"))
                .thenReturn(List.of(position(1, 10000, 100)));
        when(positionMapper.selectByStrategy("S2"))
                .thenReturn(List.of(position(1, 10000, -300)));

        service.scan();

        verify(securityService).stop(eq("S1"), any(), eq("portfolio-circuit"));
        verify(securityService).stop(eq("S2"), any(), eq("portfolio-circuit"));
        ArgumentCaptor<PortfolioCircuit> captor =
                ArgumentCaptor.forClass(PortfolioCircuit.class);
        verify(portfolioCircuitMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTripped()).isTrue();
        ArgumentCaptor<AlertEvent> evt = ArgumentCaptor.forClass(AlertEvent.class);
        verify(alertEventMapper).insert(evt.capture());
        assertThat(evt.getValue().getRuleId()).isEqualTo("portfolio.circuit");
        verify(alertNotifier).notify(any(AlertEvent.class));
    }

    @Test
    @DisplayName("回撤未超阈值 → 仅更新峰值，不停止")
    void updatesPeakOnly() {
        when(portfolioCircuitMapper.selectEnabled()).thenReturn(List.of(
                circuit(0.3, "0.0")));
        when(strategyInfoMapper.selectByStrategyId("S1")).thenReturn(running("S1"));
        when(strategyInfoMapper.selectByStrategyId("S2")).thenReturn(running("S2"));
        when(positionMapper.selectByStrategy("S1"))
                .thenReturn(List.of(position(1, 10000, 500)));
        when(positionMapper.selectByStrategy("S2"))
                .thenReturn(List.of(position(1, 10000, 500)));

        service.scan();

        verify(securityService, never()).stop(any(), any(), any());
        ArgumentCaptor<PortfolioCircuit> captor =
                ArgumentCaptor.forClass(PortfolioCircuit.class);
        verify(portfolioCircuitMapper).updateById(captor.capture());
        assertThat(captor.getValue().getPeakPnlRate()).isEqualByComparingTo("0.05");
        assertThat(captor.getValue().getTripped()).isFalse();
    }

    @Test
    @DisplayName("无运行成员 → 跳过（不评估/不告警）")
    void skipsWhenNoRunningMembers() {
        when(portfolioCircuitMapper.selectEnabled()).thenReturn(List.of(circuit(0.01, null)));
        StrategyInfo offline = running("S1");
        offline.setStatus(StrategyInfo.OFFLINE);
        when(strategyInfoMapper.selectByStrategyId("S1")).thenReturn(offline);
        when(strategyInfoMapper.selectByStrategyId("S2")).thenReturn(null);

        service.scan();

        verify(positionMapper, times(0)).selectByStrategy(anyString());
        verify(securityService, never()).stop(any(), any(), any());
        verify(alertEventMapper, never()).insert(any(AlertEvent.class));
    }
}