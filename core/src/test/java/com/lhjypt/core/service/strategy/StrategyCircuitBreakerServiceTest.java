package com.lhjypt.core.service.strategy;

import com.lhjypt.core.common.auth.UserContext;
import com.lhjypt.core.config.DistributedLock;
import com.lhjypt.core.mapper.PositionMapper;
import com.lhjypt.core.mapper.StrategyCircuitMapper;
import com.lhjypt.core.mapper.StrategyInfoMapper;
import com.lhjypt.core.model.Position;
import com.lhjypt.core.model.strategy.StrategyCircuit;
import com.lhjypt.core.model.strategy.StrategyInfo;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StrategyCircuitBreakerServiceTest {

    private StrategyCircuitMapper circuitMapper;
    private StrategyInfoMapper strategyInfoMapper;
    private PositionMapper positionMapper;
    private StrategySecurityService securityService;
    private StrategyCircuitBreakerService service;

    @BeforeEach
    void setUp() {
        circuitMapper = mock(StrategyCircuitMapper.class);
        strategyInfoMapper = mock(StrategyInfoMapper.class);
        positionMapper = mock(PositionMapper.class);
        securityService = mock(StrategySecurityService.class);
        DistributedLock lock = mock(DistributedLock.class);
        doAnswer(inv -> {
            inv.getArgument(1, Runnable.class).run();
            return true;
        }).when(lock).execute(anyString(), any(Runnable.class));
        service = new StrategyCircuitBreakerService(circuitMapper, strategyInfoMapper,
                positionMapper, securityService, lock);
    }

    private static Position position(double qty, double avgCost, double pnl) {
        Position position = new Position();
        position.setQuantity(BigDecimal.valueOf(qty));
        position.setAvgCost(BigDecimal.valueOf(avgCost));
        position.setUnrealizedPnl(BigDecimal.valueOf(pnl));
        return position;
    }

    private static StrategyCircuit circuit(double maxDrawdown, BigDecimal peak) {
        StrategyCircuit circuit = new StrategyCircuit();
        circuit.setId(1L);
        circuit.setStrategyId("S1");
        circuit.setMaxDrawdownPct(BigDecimal.valueOf(maxDrawdown));
        circuit.setEnabled(true);
        circuit.setPeakPnlRate(peak);
        circuit.setTripped(false);
        return circuit;
    }

    private static StrategyInfo running() {
        StrategyInfo info = new StrategyInfo();
        info.setStrategyId("S1");
        info.setStatus(StrategyInfo.RUNNING);
        return info;
    }

    @Test
    @DisplayName("回撤超阈值 → 自动停止策略并标记 tripped")
    void tripsWhenDrawdownExceedsLimit() {
        when(circuitMapper.selectEnabled()).thenReturn(List.of(
                circuit(0.02, new BigDecimal("0.05"))));
        when(strategyInfoMapper.selectByStrategyId("S1")).thenReturn(running());
        // 当前浮盈率 = 100/10000 = 0.01 → 回撤 0.05-0.01 = 0.04 ≥ 0.02
        when(positionMapper.selectByStrategy("S1"))
                .thenReturn(List.of(position(1, 10000, 100)));

        service.scan();

        verify(securityService).stop(eq("S1"), any(), eq("circuit-breaker"));
        ArgumentCaptor<StrategyCircuit> captor = ArgumentCaptor.forClass(StrategyCircuit.class);
        verify(circuitMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTripped()).isTrue();
        assertThat(captor.getValue().getLastTripTime()).isNotNull();
    }

    @Test
    @DisplayName("回撤未超阈值 → 仅更新峰值不停止")
    void updatesPeakWithoutTripping() {
        when(circuitMapper.selectEnabled()).thenReturn(List.of(
                circuit(0.2, new BigDecimal("0.01"))));
        when(strategyInfoMapper.selectByStrategyId("S1")).thenReturn(running());
        // 当前浮盈率 0.03 > 峰值 0.01 → 更新峰值
        when(positionMapper.selectByStrategy("S1"))
                .thenReturn(List.of(position(1, 10000, 300)));

        service.scan();

        verify(securityService, never()).stop(any(), any(), any());
        ArgumentCaptor<StrategyCircuit> captor = ArgumentCaptor.forClass(StrategyCircuit.class);
        verify(circuitMapper).updateById(captor.capture());
        assertThat(captor.getValue().getPeakPnlRate())
                .isEqualByComparingTo("0.03");
        assertThat(captor.getValue().getTripped()).isFalse();
    }

    @Test
    @DisplayName("策略未运行或无持仓 → 跳过")
    void skipsNonRunningOrNoPosition() {
        when(circuitMapper.selectEnabled()).thenReturn(List.of(circuit(0.01, null)));
        StrategyInfo offline = running();
        offline.setStatus(StrategyInfo.OFFLINE);
        when(strategyInfoMapper.selectByStrategyId("S1")).thenReturn(offline);

        service.scan();
        verify(positionMapper, never()).selectByStrategy(anyString());
        verify(securityService, never()).stop(any(), any(), any());
    }
}