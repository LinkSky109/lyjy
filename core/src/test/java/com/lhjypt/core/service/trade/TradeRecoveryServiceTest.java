package com.lhjypt.core.service.trade;

import com.lhjypt.core.config.DistributedLock;
import com.lhjypt.core.model.Order;
import com.lhjypt.core.model.trade.OrderReport;
import com.lhjypt.core.service.order.OrderManagementService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeRecoveryServiceTest {

    @Test
    void recoversActiveOrderByQueryingGateway() {
        OrderManagementService orderService = mock(OrderManagementService.class);
        TradeExecutionService executionService = mock(TradeExecutionService.class);
        DistributedLock distributedLock = mock(DistributedLock.class);
        TradeRecoveryService service = new TradeRecoveryService(orderService, executionService, distributedLock);

        Order order = new Order();
        order.setOrderId("ORD-001");
        order.setExchangeOrderId("EX000001");
        order.setGatewayId("mock-default");
        when(executionService.isAuthenticated()).thenReturn(true);
        when(orderService.getActiveOrders()).thenReturn(List.of(order));
        when(executionService.queryOrder("mock-default", "EX000001"))
                .thenReturn(new OrderReport());

        int recovered = service.recoverActiveOrders();

        assertEquals(1, recovered);
        verify(orderService).applyOrderReport(any(OrderReport.class));
    }

    @Test
    void skipsOrderWithoutExchangeOrderId() {
        OrderManagementService orderService = mock(OrderManagementService.class);
        TradeExecutionService executionService = mock(TradeExecutionService.class);
        DistributedLock distributedLock = mock(DistributedLock.class);
        TradeRecoveryService service = new TradeRecoveryService(orderService, executionService, distributedLock);

        Order order = new Order();
        order.setOrderId("ORD-001");
        when(executionService.isAuthenticated()).thenReturn(true);
        when(orderService.getActiveOrders()).thenReturn(List.of(order));

        assertEquals(0, service.recoverActiveOrders());
        verify(executionService, never()).queryOrder(any(), any());
    }

    @Test
    void scheduledRecoveryRunsThroughDistributedLock() {
        OrderManagementService orderService = mock(OrderManagementService.class);
        TradeExecutionService executionService = mock(TradeExecutionService.class);
        DistributedLock distributedLock = mock(DistributedLock.class);
        TradeRecoveryService service = new TradeRecoveryService(orderService, executionService, distributedLock);

        when(executionService.isAuthenticated()).thenReturn(true);
        when(orderService.getActiveOrders()).thenReturn(List.of());
        when(distributedLock.execute(anyString(), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    invocation.getArgument(1, Runnable.class).run();
                    return true;
                });

        service.scheduledRecovery();

        verify(distributedLock).execute(eq("trade.recovery"), any(Runnable.class));
        verify(orderService).getActiveOrders();
    }
}
