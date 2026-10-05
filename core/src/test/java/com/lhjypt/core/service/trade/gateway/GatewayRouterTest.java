package com.lhjypt.core.service.trade.gateway;

import com.lhjypt.core.common.exception.GatewayUnavailableException;
import com.lhjypt.core.mapper.AlertEventMapper;
import com.lhjypt.core.mapper.GatewayInfoMapper;
import com.lhjypt.core.mapper.GatewayRouteRuleMapper;
import com.lhjypt.core.model.gateway.GatewayInfo;
import com.lhjypt.core.model.gateway.GatewayStatus;
import com.lhjypt.core.model.gateway.GatewaySubmission;
import com.lhjypt.core.model.gateway.GatewayRouteRule;
import com.lhjypt.core.model.trade.AccountInfo;
import com.lhjypt.core.model.trade.CTPConfig;
import com.lhjypt.core.model.trade.OrderReport;
import com.lhjypt.core.model.trade.PositionDetail;
import com.lhjypt.core.model.trade.TradeReport;
import com.lhjypt.core.model.trade.TradeRequest;
import com.lhjypt.core.service.trade.TradingGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayRouterTest {

    private GatewayRegistry registry;
    private GatewayRouteService routeService;
    private KafkaTemplate<String, String> kafkaTemplate;
    private GatewayRouter router;
    private FakeGateway primary;
    private FakeGateway fallback;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        GatewayInfoMapper infoMapper = mock(GatewayInfoMapper.class);
        GatewayRouteRuleMapper routeMapper = mock(GatewayRouteRuleMapper.class);
        routeService = mock(GatewayRouteService.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        registry = new GatewayRegistry(infoMapper, mock(AlertEventMapper.class));
        router = new GatewayRouter(registry, routeService, kafkaTemplate);

        primary = new FakeGateway("P", true);
        fallback = new FakeGateway("F", true);
        registry.register(info("P", 10), primary);
        registry.register(info("F", 20), fallback);
    }

    @Test
    void choosesRuleGatewayWhenAvailable() {
        when(routeService.enabledRules()).thenReturn(List.of(rule("P", "F")));

        var decision = router.chooseRoute("A", "rb2410", "LIMIT");

        assertEquals("P", decision.gatewayId());
        assertEquals("F", decision.fallbackId());
    }

    @Test
    void switchesToFallbackWhenPrimarySubmitFails() {
        when(routeService.enabledRules()).thenReturn(List.of(rule("P", "F")));
        primary.failSubmit = true;
        TradeRequest request = request();

        GatewaySubmission submission = router.submitOrder(request);

        assertTrue(submission.fallbackUsed());
        assertEquals("F", submission.gatewayId());
        assertEquals("P", submission.switchedFrom());
        verify(kafkaTemplate).send(anyString(), anyString(), anyString());
    }

    @Test
    void skipsDownGatewayAndRoutesFallback() {
        when(routeService.enabledRules()).thenReturn(List.of(rule("P", "F")));
        primary.authenticated = false;
        for (int i = 0; i < 3; i++) {
            registry.healthCheckAll();
        }

        var decision = router.chooseRoute("A", "rb2410", "LIMIT");

        assertEquals("F", decision.gatewayId());
        assertEquals("primary-unavailable", decision.reason());
    }

    @Test
    void rejectsWhenNoGatewayAvailable() {
        when(routeService.enabledRules()).thenReturn(List.of());
        primary.authenticated = false;
        fallback.authenticated = false;
        for (int i = 0; i < 3; i++) {
            registry.healthCheckAll();
        }

        assertThrows(GatewayUnavailableException.class,
                () -> router.chooseRoute("A", "rb2410", "LIMIT"));
    }

    @Test
    void accountAndSymbolMismatchFallsBackToDefaultPriority() {
        GatewayRouteRule rule = rule("P", "F");
        rule.setAccountId("OTHER");
        rule.setSymbolRegex("cu.*");
        when(routeService.enabledRules()).thenReturn(List.of(rule));

        var decision = router.chooseRoute("A", "rb2410", "LIMIT");

        assertEquals("P", decision.gatewayId());
        assertEquals("default-priority", decision.reason());
    }

    @Test
    void attachesListenersToGatewaysRegisteredAfterListenerSetup() {
        router.registerReportListeners(report -> { }, report -> { });
        FakeGateway late = new FakeGateway("LATE", true);

        router.register(info("LATE", 30), late);

        assertEquals(1, late.orderListeners.size());
        assertEquals(1, late.tradeListeners.size());
    }

    private static GatewayRouteRule rule(String primary, String fallback) {
        GatewayRouteRule rule = new GatewayRouteRule();
        rule.setRuleId("R-1");
        rule.setAccountId("A");
        rule.setGatewayId(primary);
        rule.setFallbackId(fallback);
        rule.setPriority(10);
        rule.setEnabled(true);
        return rule;
    }

    private static GatewayInfo info(String id, int priority) {
        GatewayInfo info = new GatewayInfo();
        info.setGatewayId(id);
        info.setGatewayType("MOCK");
        info.setName(id);
        info.setStatus(GatewayStatus.READY.name());
        info.setPriority(priority);
        return info;
    }

    private static TradeRequest request() {
        TradeRequest request = new TradeRequest();
        request.setAccountId("A");
        request.setStrategyId("S");
        request.setSymbol("rb2410");
        request.setOrderType("LIMIT");
        request.setDirection("BUY");
        request.setOffsetFlag("OPEN");
        request.setPrice(java.math.BigDecimal.ONE);
        request.setQuantity(java.math.BigDecimal.ONE);
        return request;
    }

    private static class FakeGateway implements TradingGateway {
        private final String name;
        private final AtomicBoolean connected = new AtomicBoolean(true);
        private volatile boolean authenticated;
        private volatile boolean failSubmit;
        private final List<OrderReportListener> orderListeners = new ArrayList<>();
        private final List<TradeReportListener> tradeListeners = new ArrayList<>();

        FakeGateway(String name, boolean authenticated) {
            this.name = name;
            this.authenticated = authenticated;
        }

        @Override public void connect(CTPConfig config) { connected.set(true); authenticated = true; }
        @Override public void disconnect() { connected.set(false); authenticated = false; }
        @Override public boolean isConnected() { return connected.get(); }
        @Override public boolean isAuthenticated() { return authenticated; }
        @Override public String submitOrder(TradeRequest request) {
            if (failSubmit) throw new IllegalStateException("submit failed");
            return name + "-REF";
        }
        @Override public boolean cancelOrder(String exchangeOrderId, String symbol) { return true; }
        @Override public OrderReport queryOrder(String exchangeOrderId) { return null; }
        @Override public List<TradeReport> queryTrades() { return List.of(); }
        @Override public List<PositionDetail> queryPositions() { return List.of(); }
        @Override public AccountInfo queryAccount() { return new AccountInfo(); }
        @Override public void addOrderReportListener(OrderReportListener listener) {
            orderListeners.add(listener);
        }
        @Override public void addTradeReportListener(TradeReportListener listener) {
            tradeListeners.add(listener);
        }
        @Override public String getName() { return name; }
    }
}
