package com.lhjypt.core.service.degrade;

import com.lhjypt.core.mapper.AlertEventMapper;
import com.lhjypt.core.mapper.DegradeSwitchMapper;
import com.lhjypt.core.model.degrade.DegradeSwitch;
import com.lhjypt.core.model.monitor.AlertEvent;
import com.lhjypt.core.service.market.impl.MockMarketDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 性能降级预案行为测试（TASK-P1-004）
 */
class DegradeServiceTest {

    private DegradeSwitchMapper switchMapper;
    private AlertEventMapper alertEventMapper;
    private MockMarketDataProvider marketProvider;
    private AtomicLong clock; // 纳秒时钟（可推进）
    private DegradeService service;

    /** 模拟开关表 */
    private final List<DegradeSwitch> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        switchMapper = mock(DegradeSwitchMapper.class);
        alertEventMapper = mock(AlertEventMapper.class);
        marketProvider = mock(MockMarketDataProvider.class);
        clock = new AtomicLong(0);
        stored.clear();
        when(switchMapper.selectList(any())).thenAnswer(i -> new ArrayList<>(stored));
        when(switchMapper.selectByKey(any())).thenAnswer(i -> stored.stream()
                .filter(s -> s.getSwitchKey().equals(i.getArgument(0)))
                .findFirst().orElse(null));
        when(switchMapper.insert(any(DegradeSwitch.class))).thenAnswer(i -> {
            stored.add(i.getArgument(0));
            return 1;
        });
        when(switchMapper.updateById(any(DegradeSwitch.class))).thenAnswer(i -> {
            DegradeSwitch upd = i.getArgument(0);
            stored.removeIf(s -> s.getSwitchKey().equals(upd.getSwitchKey()));
            stored.add(upd);
            return 1;
        });
        service = new DegradeService(switchMapper, alertEventMapper, marketProvider,
                clock::get);
        service.registerDefaults();
    }

    // ---------- 令牌桶限流 ----------

    @Test
    @DisplayName("令牌桶：容量内放行，超出拒绝，随时间补充")
    void tokenBucketAdmitsAndRefills() {
        TokenBucket bucket = new TokenBucket(100, 10, clock::get);
        for (int i = 0; i < 100; i++) {
            assertThat(bucket.tryAcquire()).as("permit #" + i).isTrue();
        }
        assertThat(bucket.tryAcquire()).isFalse();

        clock.addAndGet(1_000_000_000L); // 1 秒 → 补 10 个令牌
        int admitted = 0;
        for (int i = 0; i < 20; i++) if (bucket.tryAcquire()) admitted++;
        assertThat(admitted).isEqualTo(10);
    }

    // ---------- 熔断器 ----------

    @Test
    @DisplayName("熔断：连续3次失败→OPEN拒绝，窗口过→HALF_OPEN探测，成功→CLOSED")
    void circuitBreakerLifecycle() {
        SimpleCircuitBreaker cb =
                new SimpleCircuitBreaker(3, 60_000_000_000L, clock::get); // 60s 窗口
        assertThat(cb.state()).isEqualTo(SimpleCircuitBreaker.State.CLOSED);

        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.allowRequest()).isTrue(); // 2 次未达阈值
        cb.recordFailure();
        assertThat(cb.state()).isEqualTo(SimpleCircuitBreaker.State.OPEN);
        assertThat(cb.allowRequest()).isFalse();

        clock.addAndGet(60_000_000_000L);
        assertThat(cb.state()).isEqualTo(SimpleCircuitBreaker.State.HALF_OPEN);
        assertThat(cb.allowRequest()).isTrue();

        cb.recordSuccess();
        assertThat(cb.state()).isEqualTo(SimpleCircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("熔断：HALF_OPEN 探测失败立即重新 OPEN")
    void halfOpenFailureRetrips() {
        SimpleCircuitBreaker cb =
                new SimpleCircuitBreaker(1, 10_000_000_000L, clock::get);
        cb.recordFailure();
        assertThat(cb.state()).isEqualTo(SimpleCircuitBreaker.State.OPEN);
        clock.addAndGet(10_000_000_000L);
        assertThat(cb.state()).isEqualTo(SimpleCircuitBreaker.State.HALF_OPEN);
        cb.recordFailure();
        assertThat(cb.state()).isEqualTo(SimpleCircuitBreaker.State.OPEN);
        assertThat(cb.allowRequest()).isFalse();
    }

    // ---------- 开关管理 ----------

    @Test
    @DisplayName("设置行情备用开关 ON → 落库 + 行情源降频 + alert_event 联动")
    void marketBackupSwitchTogglesProvider() {
        service.setSwitch("degrade.market.backup", "ON", "admin", "主源抖动");

        DegradeSwitch sw = stored.stream()
                .filter(s -> s.getSwitchKey().equals("degrade.market.backup"))
                .findFirst().orElseThrow();
        assertThat(sw.getLevel()).isEqualTo("ON");
        assertThat(sw.getOperator()).isEqualTo("admin");
        verify(marketProvider).setTickIntervalMillis(5000); // 降频到快照级
        verify(alertEventMapper).insert(any(AlertEvent.class));
        assertThat(service.isOn("degrade.market.backup")).isTrue();
    }

    @Test
    @DisplayName("开关恢复 OFF → 行情源回到正常频率")
    void marketBackupOffRestoresProvider() {
        service.setSwitch("degrade.market.backup", "ON", "admin", "x");
        service.setSwitch("degrade.market.backup", "OFF", "admin", "恢复");

        verify(marketProvider).setTickIntervalMillis(500);
        assertThat(service.isOn("degrade.market.backup")).isFalse();
    }

    @Test
    @DisplayName("核心链路开关（报单/撤单）永不允许降级")
    void corePathSwitchRejected() {
        int before = stored.size();
        org.mockito.Mockito.clearInvocations(switchMapper);
        assertThatThrownBy(() ->
                service.setSwitch("degrade.core.order", "ON", "admin", "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("核心");
        verify(switchMapper, never()).insert(any(DegradeSwitch.class));
        assertThat(stored).hasSize(before);
    }

    @Test
    @DisplayName("未知开关 key 拒绝")
    void unknownKeyRejected() {
        assertThatThrownBy(() ->
                service.setSwitch("degrade.nope.xyz", "ON", "admin", "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("非核心功能开关：VaR ON 时 isOn 生效且行情不受影响")
    void nonCoreFeatureSwitch() {
        service.setSwitch("degrade.feature.var", "ON", "admin", "算力不足");
        assertThat(service.isOn("degrade.feature.var")).isTrue();
        verify(marketProvider, never()).setTickIntervalMillis(5000);
    }

    // ---------- 容量水位 ----------

    @Test
    @DisplayName("订单入口限流：突发 600 笔仅放行容量 500，并上报水位")
    void orderRateLimitRejectsOverCapacity() {
        int admitted = 0;
        for (int i = 0; i < 600; i++) {
            if (service.tryAcquireOrderPermit()) admitted++;
        }
        assertThat(admitted).isEqualTo(500); // 容量内全放行，超出拒绝

        var water = service.capacityWaterLevel();
        assertThat(water).containsKeys("orderTpsCapacity", "orderTokensAvailable",
                "activeSwitches", "breakers");
        assertThat(((Number) water.get("orderTpsCapacity")).longValue()).isEqualTo(500);
        assertThat(((List<?>) water.get("activeSwitches"))).isEmpty();
    }
}
