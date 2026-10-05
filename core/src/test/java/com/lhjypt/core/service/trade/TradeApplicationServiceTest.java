package com.lhjypt.core.service.trade;

import com.lhjypt.core.common.exception.TradeRejectedException;
import com.lhjypt.core.model.Order;
import com.lhjypt.core.model.order.CreateOrderCommand;
import com.lhjypt.core.model.risk.RiskCheckResult;
import com.lhjypt.core.model.trade.TradeRequest;
import com.lhjypt.core.model.trade.TradeSubmissionResult;
import com.lhjypt.core.service.fund.FundReservationService;
import com.lhjypt.core.service.order.OrderManagementService;
import com.lhjypt.core.service.order.OrderStatus;
import com.lhjypt.core.service.risk.PreTradeRiskService;
import com.lhjypt.core.service.risk.RiskRule;
import com.lhjypt.core.service.risk.TradingRiskContextFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeApplicationServiceTest {

    private PreTradeRiskService riskService;
    private TradingRiskContextFactory contextFactory;
    private OrderManagementService orderService;
    private FundReservationService reservationService;
    private TradeCostCalculator costCalculator;
    private TradeExecutionService executionService;
    private TradeApplicationService service;

    @BeforeEach
    void setUp() {
        riskService = mock(PreTradeRiskService.class);
        contextFactory = mock(TradingRiskContextFactory.class);
        orderService = mock(OrderManagementService.class);
        reservationService = mock(FundReservationService.class);
        costCalculator = mock(TradeCostCalculator.class);
        executionService = mock(TradeExecutionService.class);
        service = new TradeApplicationService(
                riskService, contextFactory, orderService,
                reservationService, costCalculator, executionService);
        when(contextFactory.create(any())).thenReturn(mock(RiskRule.RiskContext.class));
    }

    @Test
    void submitsOrderThroughRiskOrderReservationAndGateway() {
        TradeRequest request = request();
        Order order = pendingOrder();
        when(riskService.check(any(), any())).thenReturn(RiskCheckResult.pass());
        when(orderService.createOrder(any(CreateOrderCommand.class))).thenReturn(order);
        when(costCalculator.estimate(request)).thenReturn(new TradeCostCalculator.TradeCost(
                new BigDecimal("7000"), new BigDecimal("700"), new BigDecimal("2.1")));
        when(executionService.placeOrder(any(TradeRequest.class))).thenReturn("000001");
        when(executionService.getMode()).thenReturn("MOCK");
        when(orderService.markSubmitted("ORD-001", "000001")).thenAnswer(invocation -> {
            order.setStatus(OrderStatus.SUBMITTED.getCode());
            order.setBrokerOrderRef("000001");
            return order;
        });

        TradeSubmissionResult result = service.submit(request);

        assertEquals("ORD-001", result.orderId());
        assertEquals("SUBMITTED", result.status());
        assertEquals("000001", result.brokerOrderRef());
        assertFalse(result.replay());
        verify(reservationService).reserve(order, request, new TradeCostCalculator.TradeCost(
                new BigDecimal("7000"), new BigDecimal("700"), new BigDecimal("2.1")));
    }

    @Test
    void rejectsBeforeCreatingOrderWhenRiskFails() {
        TradeRequest request = request();
        RiskCheckResult rejected = RiskCheckResult.pass();
        rejected.addViolation("POSITION_LIMIT", "持仓限额", "超限", "REJECT");
        when(riskService.check(any(), any())).thenReturn(rejected);

        TradeRejectedException error = assertThrows(
                TradeRejectedException.class, () -> service.submit(request));

        assertEquals("RISK_REJECTED", error.getCode());
        assertTrue(error.getViolations().get(0).contains("超限"));
        verify(orderService, never()).createOrder(any());
        verify(executionService, never()).placeOrder(any());
    }

    @Test
    void returnsExistingOrderOnIdempotentReplay() {
        TradeRequest request = request();
        Order order = pendingOrder();
        order.setStatus(OrderStatus.SUBMITTED.getCode());
        when(riskService.check(any(), any())).thenReturn(RiskCheckResult.pass());
        when(orderService.createOrder(any(CreateOrderCommand.class))).thenReturn(order);
        when(executionService.getMode()).thenReturn("MOCK");

        TradeSubmissionResult result = service.submit(request);

        assertTrue(result.replay());
        verify(reservationService, never()).reserve(any(), any(), any());
        verify(executionService, never()).placeOrder(any());
    }

    @Test
    void compensatesReservationWhenGatewayFails() {
        TradeRequest request = request();
        Order order = pendingOrder();
        when(riskService.check(any(), any())).thenReturn(RiskCheckResult.pass());
        when(orderService.createOrder(any(CreateOrderCommand.class))).thenReturn(order);
        when(costCalculator.estimate(request)).thenReturn(new TradeCostCalculator.TradeCost(
                new BigDecimal("7000"), new BigDecimal("700"), new BigDecimal("2.1")));
        when(executionService.placeOrder(any(TradeRequest.class)))
                .thenThrow(new IllegalStateException("gateway down"));

        TradeRejectedException error = assertThrows(
                TradeRejectedException.class, () -> service.submit(request));

        assertEquals("GATEWAY_UNAVAILABLE", error.getCode());
        verify(reservationService).releaseRemaining("ORD-001");
        verify(orderService).reject("ORD-001", "GATEWAY_UNAVAILABLE", "gateway down");
    }

    @Test
    void requiresIdempotencyKey() {
        TradeRequest request = request();
        request.setIdempotencyKey(null);

        TradeRejectedException error = assertThrows(
                TradeRejectedException.class, () -> service.submit(request));

        assertEquals("VALIDATION_FAILED", error.getCode());
        verify(contextFactory, never()).create(any());
    }

    @Test
    void cancelsSubmittedOrderAndReleasesRemainingFunds() {
        Order order = pendingOrder();
        order.setStatus(OrderStatus.ACCEPTED.getCode());
        order.setExchangeOrderId("EX000001");
        when(orderService.getOrderByOrderId("ORD-001")).thenReturn(order);
        when(executionService.cancelOrder("EX000001", "rb2410")).thenReturn(true);
        when(orderService.cancel("ORD-001", "用户撤单")).thenReturn(order);

        Order result = service.cancel("ORD-001", "用户撤单");

        assertEquals("ORD-001", result.getOrderId());
        verify(reservationService).releaseRemaining("ORD-001");
    }

    @Test
    void rejectsCancelBeforeGatewayConfirmation() {
        Order order = pendingOrder();
        order.setStatus(OrderStatus.SUBMITTED.getCode());
        when(orderService.getOrderByOrderId("ORD-001")).thenReturn(order);

        assertThrows(IllegalStateException.class,
                () -> service.cancel("ORD-001", "用户撤单"));
        verify(executionService, never()).cancelOrder(any(), any());
    }

    private TradeRequest request() {
        TradeRequest request = new TradeRequest();
        request.setAccountId("DEFAULT-001");
        request.setStrategyId("S-001");
        request.setSymbol("rb2410");
        request.setExchange("SHFE");
        request.setDirection("BUY");
        request.setOffsetFlag("OPEN");
        request.setOrderType("LIMIT");
        request.setPrice(new BigDecimal("3500"));
        request.setQuantity(new BigDecimal("2"));
        request.setIdempotencyKey("IDEM-001");
        return request;
    }

    private Order pendingOrder() {
        Order order = new Order();
        order.setOrderId("ORD-001");
        order.setAccountId("DEFAULT-001");
        order.setStrategyId("S-001");
        order.setSymbol("rb2410");
        order.setDirection("BUY");
        order.setOffsetFlag("OPEN");
        order.setOrderType("LIMIT");
        order.setPrice(new BigDecimal("3500"));
        order.setQuantity(new BigDecimal("2"));
        order.setStatus(OrderStatus.PENDING.getCode());
        return order;
    }
}
