package com.lhjypt.core.service.strategy;

import com.lhjypt.core.common.auth.UserContext;
import com.lhjypt.core.config.DistributedLock;
import com.lhjypt.core.config.StrategyWatchdogProperties;
import com.lhjypt.core.model.strategy.WatchdogEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StrategyWatchdogServiceTest {

    private StrategySecurityService securityService;
    private StrategyWatchdogProperties properties;
    private StrategyWatchdogService service;

    @BeforeEach
    void setUp() {
        securityService = mock(StrategySecurityService.class);
        properties = new StrategyWatchdogProperties();
        properties.setEnabled(true);
        properties.setScanIntervalMs(30_000);
        DistributedLock lock = mock(DistributedLock.class);
        // 锁直接执行任务
        doAnswer(inv -> {
            inv.getArgument(1, Runnable.class).run();
            return true;
        }).when(lock).execute(any(), any(Runnable.class));
        service = new StrategyWatchdogService(securityService, properties, lock);
    }

    private static final UserContext TRADER = UserContext.of("trader-1", "TRADER", null);
    private static final UserContext ADMIN = UserContext.of("admin", "ADMIN", null);

    @AfterEach
    void clear() {
        // 每次用例独立服务实例
    }

    @Test
    @DisplayName("值守启用后：运行中跳过，不拉起")
    void runningStrategyIsSkipped() {
        service.setWatchdog("S1", true, 30_000L, TRADER);

        when(securityService.sandboxStatus(eq("S1"), any()))
                .thenReturn(Map.of("status", "RUNNING"));
        service.scan();

        verify(securityService, never()).run(eq("S1"), any(), any());
        assertThat(service.list().get(0).getLastStatus()).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("未运行且过冷却 → 自动拉起；冷却期内不重复拉起")
    void offlineStrategyIsLiftedAfterCooldown() {
        service.setWatchdog("S1", true, 1_000L, TRADER); // 冷却 1s
        when(securityService.sandboxStatus(eq("S1"), any()))
                .thenReturn(Map.of("status", "OFFLINE"))
                .thenReturn(Map.of("status", "RUNNING"));
        when(securityService.run(eq("S1"), any(), any()))
                .thenReturn(Map.of("status", "RUNNING"));

        service.scan(); // 第一次：拉起
        verify(securityService, times(1)).run(eq("S1"), any(), any());

        service.scan(); // 冷却内（1s 未过）：不拉起
        verify(securityService, times(1)).run(eq("S1"), any(), any());
    }

    @Test
    @DisplayName("拉起失败记录 lastError 并进入冷却（不反复拉起）")
    void liftFailureIsCooldowned() {
        service.setWatchdog("S1", true, 5_000L, TRADER);
        when(securityService.sandboxStatus(eq("S1"), any()))
                .thenReturn(Map.of("status", "OFFLINE"));
        doThrow(new IllegalStateException("环境绑定失败"))
                .when(securityService).run(eq("S1"), any(), any());

        service.scan();
        service.scan();

        verify(securityService, times(1)).run(eq("S1"), any(), any());
        assertThat(service.list().get(0).getLastError()).contains("环境绑定失败");
    }

    @Test
    @DisplayName("全局关闭时不扫描；非归属用户设置被拒")
    void respectsGateAndOwnership() {
        properties.setEnabled(false);
        service.scan();
        verify(securityService, never()).sandboxStatus(any(), any());

        doThrow(new IllegalStateException("无权访问该策略"))
                .when(securityService).requireStrategyOwnerOrAdmin("S2", TRADER);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.setWatchdog("S2", true, null, TRADER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("无权");
    }
}