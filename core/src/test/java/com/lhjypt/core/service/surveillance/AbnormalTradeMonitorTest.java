package com.lhjypt.core.service.surveillance;

import com.lhjypt.core.mapper.AbnormalTradeAlertMapper;
import com.lhjypt.core.mapper.OrderMapper;
import com.lhjypt.core.model.Order;
import com.lhjypt.core.model.surveillance.AbnormalTradeAlert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 异常交易监控四类评分行为测试（TASK-P1-003）
 */
class AbnormalTradeMonitorTest {

    private OrderMapper orderMapper;
    private AbnormalTradeAlertMapper alertMapper;
    private AbnormalTradeMonitor monitor;

    /** 模拟告警表存储 */
    private final List<AbnormalTradeAlert> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        orderMapper = mock(OrderMapper.class);
        alertMapper = mock(AbnormalTradeAlertMapper.class);
        stored.clear();
        when(alertMapper.selectOpen()).thenAnswer(i -> new ArrayList<>(stored));
        when(alertMapper.insert(any(AbnormalTradeAlert.class))).thenAnswer(i -> {
            stored.add(i.getArgument(0));
            return 1;
        });
        when(alertMapper.selectByAlertId(any(String.class))).thenAnswer(i ->
                stored.stream().filter(a -> a.getAlertId().equals(i.getArgument(0)))
                        .findFirst().orElse(null));
        monitor = new AbnormalTradeMonitor(orderMapper, alertMapper);
    }

    private Order order(String strategy, String symbol, String direction, String status,
                        BigDecimal qty, BigDecimal filled, BigDecimal avgPrice,
                        LocalDateTime t) {
        Order o = new Order();
        o.setStrategyId(strategy);
        o.setSymbol(symbol);
        o.setDirection(direction);
        o.setStatus(status);
        o.setQuantity(qty);
        o.setFilledQuantity(filled);
        o.setAvgFillPrice(avgPrice);
        o.setCreateTime(t);
        return o;
    }

    @Test
    @DisplayName("SELF_TRADE：同一策略同标的双向成交 → 生成 CRITICAL 级告警")
    void selfTradeDetected() {
        LocalDateTime now = LocalDateTime.now();
        when(orderMapper.selectCreatedSince(any())).thenReturn(List.of(
                order("S1", "rb2410", "BUY", "FILLED", new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("3500"), now),
                order("S1", "rb2410", "SELL", "FILLED", new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("3502"), now)));

        List<AbnormalTradeAlert> alerts = monitor.scanWindow(300);

        assertThat(alerts).hasSize(1);
        AbnormalTradeAlert a = alerts.get(0);
        assertThat(a.getRuleCode()).isEqualTo("SELF_TRADE");
        assertThat(a.getStrategyId()).isEqualTo("S1");
        assertThat(a.getSymbol()).isEqualTo("rb2410");
        assertThat(a.getScore().doubleValue()).isGreaterThanOrEqualTo(0.9);
        assertThat(a.getSeverity()).isEqualTo("CRITICAL");
        assertThat(a.getStatus()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("幂等去重：同维度 OPEN 告警已存在 → 不再重复生成")
    void deduplicationByOpenAlert() {
        LocalDateTime now = LocalDateTime.now();
        List<Order> orders = List.of(
                order("S1", "rb2410", "BUY", "FILLED", new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("3500"), now),
                order("S1", "rb2410", "SELL", "FILLED", new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("3502"), now));
        when(orderMapper.selectCreatedSince(any())).thenReturn(orders);

        monitor.scanWindow(300);
        List<AbnormalTradeAlert> second = monitor.scanWindow(300);

        assertThat(second).isEmpty();
        assertThat(stored).hasSize(1);
    }

    @Test
    @DisplayName("FREQ_CANCEL：撤单率 70% 超阈值 60% → 命中；样本不足 5 笔不判")
    void freqCancelDetected() {
        LocalDateTime now = LocalDateTime.now();
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            orders.add(order("S2", "hc2410", "BUY", i < 7 ? "CANCELED" : "FILLED",
                    new BigDecimal("5"), i < 7 ? BigDecimal.ZERO : new BigDecimal("5"),
                    i < 7 ? null : new BigDecimal("3600"), now));
        }
        when(orderMapper.selectCreatedSince(any())).thenReturn(orders);
        assertThat(monitor.scanWindow(300)).anySatisfy(a -> {
            assertThat(a.getRuleCode()).isEqualTo("FREQ_CANCEL");
            assertThat(a.getStrategyId()).isEqualTo("S2");
        });

        // 小样本：仅 4 笔（3 撤）FREQ_CANCEL 不判（样本<5 门槛），其他规则不受此约束
        stored.clear();
        when(orderMapper.selectCreatedSince(any())).thenReturn(orders.subList(0, 4));
        assertThat(monitor.scanWindow(300))
                .noneSatisfy(a -> assertThat(a.getRuleCode()).isEqualTo("FREQ_CANCEL"));
    }

    @Test
    @DisplayName("FALSE_ORDER：零成交大额撤单占比超 70% → 命中")
    void falseOrderDetected() {
        LocalDateTime now = LocalDateTime.now();
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            orders.add(order("S3", "i2409", "BUY", "CANCELED",
                    new BigDecimal("100"), BigDecimal.ZERO, null, now));
        }
        orders.add(order("S3", "i2409", "BUY", "FILLED",
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("7000"), now));
        when(orderMapper.selectCreatedSince(any())).thenReturn(orders);

        assertThat(monitor.scanWindow(300)).anySatisfy(a -> {
            assertThat(a.getRuleCode()).isEqualTo("FALSE_ORDER");
            assertThat(a.getScore().doubleValue()).isGreaterThanOrEqualTo(0.5);
        });
    }

    @Test
    @DisplayName("PUMP_DUMP：窗口内成交均价上涨超 3% → 命中并归因主动买方向策略")
    void pumpDumpDetected() {
        LocalDateTime now = LocalDateTime.now();
        when(orderMapper.selectCreatedSince(any())).thenReturn(List.of(
                order("S4", "ag2412", "BUY", "FILLED", new BigDecimal("5"), new BigDecimal("5"), new BigDecimal("6000"), now.minusSeconds(120)),
                order("S4", "ag2412", "BUY", "FILLED", new BigDecimal("5"), new BigDecimal("5"), new BigDecimal("6300"), now)));

        assertThat(monitor.scanWindow(300)).anySatisfy(a -> {
            assertThat(a.getRuleCode()).isEqualTo("PUMP_DUMP");
            assertThat(a.getSymbol()).isEqualTo("ag2412");
            assertThat(a.getStrategyId()).isEqualTo("S4");
            assertThat(a.getDetailJson()).contains("拉抬");
        });
    }

    @Test
    @DisplayName("全部低于阈值：正常交易流不产生任何告警")
    void normalFlowNoAlert() {
        LocalDateTime now = LocalDateTime.now();
        when(orderMapper.selectCreatedSince(any())).thenReturn(List.of(
                order("S5", "rb2410", "BUY", "FILLED", new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("3500"), now),
                order("S5", "rb2410", "BUY", "FILLED", new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("3501"), now),
                order("S5", "rb2410", "SELL", "FILLED", new BigDecimal("5"), new BigDecimal("5"), new BigDecimal("3502"), now)));
        // S5 双向成交会命中 SELF_TRADE，用纯单向场景验证不误报
        when(orderMapper.selectCreatedSince(any())).thenReturn(List.of(
                order("S5", "rb2410", "BUY", "FILLED", new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("3500"), now),
                order("S5", "rb2410", "BUY", "FILLED", new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("3501"), now.minusSeconds(60))));

        assertThat(monitor.scanWindow(300)).isEmpty();
        verify(alertMapper, times(0)).insert(any());
    }

    @Test
    @DisplayName("阈值热更新：调高 FREQ_CANCEL 阈值后同数据不再命中")
    void thresholdAdjustable() {
        LocalDateTime now = LocalDateTime.now();
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            orders.add(order("S6", "rb2410", "BUY", i < 7 ? "CANCELED" : "FILLED",
                    new BigDecimal("5"), i < 7 ? BigDecimal.ZERO : new BigDecimal("5"),
                    i < 7 ? null : new BigDecimal("3500"), now));
        }
        when(orderMapper.selectCreatedSince(any())).thenReturn(orders);
        assertThat(monitor.scanWindow(300))
                .anySatisfy(a -> assertThat(a.getRuleCode()).isEqualTo("FREQ_CANCEL"));

        monitor.updateThreshold("FREQ_CANCEL", 0.9);
        stored.clear();
        assertThat(monitor.scanWindow(300))
                .noneSatisfy(a -> assertThat(a.getRuleCode()).isEqualTo("FREQ_CANCEL"));
    }

    @Test
    @DisplayName("未知规则代码调整阈值应报错")
    void unknownRuleRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> monitor.updateThreshold("NO_SUCH_RULE", 1.0));
    }
}
