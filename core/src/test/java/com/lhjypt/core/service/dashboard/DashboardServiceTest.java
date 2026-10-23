package com.lhjypt.core.service.dashboard;

import com.lhjypt.core.mapper.AlertEventMapper;
import com.lhjypt.core.mapper.FundAccountMapper;
import com.lhjypt.core.mapper.OrderMapper;
import com.lhjypt.core.model.FundAccount;
import com.lhjypt.core.model.Order;
import com.lhjypt.core.model.monitor.AlertEvent;
import com.lhjypt.core.service.market.MarketDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private FundAccountMapper fundAccountMapper;
    private OrderMapper orderMapper;
    private AlertEventMapper alertEventMapper;
    private MarketDataService marketDataService;
    private HealthEndpoint healthEndpoint;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        fundAccountMapper = mock(FundAccountMapper.class);
        orderMapper = mock(OrderMapper.class);
        alertEventMapper = mock(AlertEventMapper.class);
        marketDataService = mock(MarketDataService.class);
        healthEndpoint = mock(HealthEndpoint.class);
        service = new DashboardService(fundAccountMapper, orderMapper, alertEventMapper,
                marketDataService, healthEndpoint);
    }

    @Test
    @DisplayName("汇总账户资金、盈亏、告警与委托计数")
    void summaryAggregatesExistingData() {
        when(fundAccountMapper.selectList(any())).thenReturn(List.of(
                account("A", "100000", "80000", "5000", "15000", "120", "-20"),
                account("B", "50000", "40000", "2000", "8000", "30", "10")));
        when(alertEventMapper.selectByStatus("OPEN")).thenReturn(List.of(
                alert("CRITICAL"), alert("WARN")));
        when(orderMapper.selectActiveOrders()).thenReturn(List.of(new Order(), new Order()));
        when(orderMapper.selectCreatedSince(any())).thenReturn(List.of(
                order("FILLED"), order("PENDING"), order("FILLED")));
        when(marketDataService.isRunning()).thenReturn(true);
        when(marketDataService.getSubscribedSymbols()).thenReturn(Set.of("BTC_USDT", "ETH_USDT"));
        HealthComponent up = component(Status.UP);
        HealthComponent down = component(Status.DOWN);
        when(healthEndpoint.health()).thenReturn(up);
        when(healthEndpoint.healthForPath("db", "primaryDataSource")).thenReturn(up);
        when(healthEndpoint.healthForPath("db", "clickHouseDataSource")).thenReturn(up);
        when(healthEndpoint.healthForPath("redis")).thenReturn(down);
        when(healthEndpoint.healthForPath("kafka")).thenThrow(new IllegalArgumentException("missing"));

        Map<String, Object> summary = service.summary();

        assertThat(((Map<?, ?>) summary.get("service")).get("status")).isEqualTo("UP");
        assertThat(((Map<?, ?>) summary.get("market")).get("running")).isEqualTo(true);
        assertThat(((Map<?, ?>) summary.get("market")).get("subscribed")).isEqualTo(2);
        assertThat(((Map<?, ?>) summary.get("components")).get("service")).isEqualTo("UP");
        assertThat(((Map<?, ?>) summary.get("components")).get("redis")).isEqualTo("DOWN");
        assertThat(((Map<?, ?>) summary.get("components")).get("kafka")).isEqualTo("UNKNOWN");

        Map<?, ?> equity = (Map<?, ?>) summary.get("equity");
        assertThat(equity.get("total")).isEqualTo(new BigDecimal("150000"));
        assertThat(equity.get("available")).isEqualTo(new BigDecimal("120000"));
        assertThat(equity.get("frozen")).isEqualTo(new BigDecimal("7000"));
        assertThat(equity.get("margin")).isEqualTo(new BigDecimal("23000"));
        assertThat(equity.get("marginRatio")).isEqualTo(new BigDecimal("15.3"));
        assertThat(equity.get("accounts")).isEqualTo(2);

        Map<?, ?> pnl = (Map<?, ?>) summary.get("pnl");
        assertThat(pnl.get("unrealized")).isEqualTo(new BigDecimal("150"));
        assertThat(pnl.get("realized")).isEqualTo(new BigDecimal("-10"));
        assertThat(pnl.get("combined")).isEqualTo(new BigDecimal("140"));
        assertThat(pnl.get("basis")).isEqualTo("CUMULATIVE");

        assertThat(((Map<?, ?>) summary.get("alerts")).get("open")).isEqualTo(2);
        assertThat(((Map<?, ?>) summary.get("alerts")).get("critical")).isEqualTo(1L);
        assertThat(((Map<?, ?>) summary.get("orders")).get("active")).isEqualTo(2);
        assertThat(((Map<?, ?>) summary.get("orders")).get("filledToday")).isEqualTo(2L);
        assertThat(summary.get("asOf")).isNotNull();
    }

    @Test
    @DisplayName("无账户时资金与占用率归零")
    void summaryHandlesEmptyAccounts() {
        when(fundAccountMapper.selectList(any())).thenReturn(List.of());
        when(alertEventMapper.selectByStatus("OPEN")).thenReturn(List.of());
        when(orderMapper.selectActiveOrders()).thenReturn(List.of());
        when(orderMapper.selectCreatedSince(any())).thenReturn(List.of());
        when(marketDataService.isRunning()).thenReturn(false);
        when(marketDataService.getSubscribedSymbols()).thenReturn(Set.of());
        when(healthEndpoint.health()).thenThrow(new IllegalStateException("down"));

        Map<String, Object> summary = service.summary();

        assertThat(((Map<?, ?>) summary.get("service")).get("status")).isEqualTo("UNKNOWN");
        Map<?, ?> equity = (Map<?, ?>) summary.get("equity");
        assertThat(equity.get("total")).isEqualTo(BigDecimal.ZERO);
        assertThat(equity.get("marginRatio")).isEqualTo(BigDecimal.ZERO);
        assertThat(equity.get("accounts")).isEqualTo(0);
        assertThat(((Map<?, ?>) summary.get("components")).get("postgres")).isEqualTo("UNKNOWN");
    }

    private static FundAccount account(String id, String total, String available, String frozen,
                                       String margin, String unrealized, String realized) {
        FundAccount account = new FundAccount();
        account.setAccountId(id);
        account.setTotalAsset(new BigDecimal(total));
        account.setAvailableCash(new BigDecimal(available));
        account.setFrozenCash(new BigDecimal(frozen));
        account.setMargin(new BigDecimal(margin));
        account.setUnrealizedPnl(new BigDecimal(unrealized));
        account.setRealizedPnl(new BigDecimal(realized));
        return account;
    }

    private static AlertEvent alert(String severity) {
        AlertEvent event = new AlertEvent();
        event.setEventId("E-" + severity);
        event.setSeverity(severity);
        event.setStatus("OPEN");
        return event;
    }

    private static Order order(String status) {
        Order order = new Order();
        order.setStatus(status);
        return order;
    }

    private static HealthComponent component(Status status) {
        HealthComponent component = mock(HealthComponent.class);
        when(component.getStatus()).thenReturn(status);
        return component;
    }
}
