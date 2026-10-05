package com.lhjypt.core.service.fund;

import com.lhjypt.core.mapper.OrderFundReservationMapper;
import com.lhjypt.core.model.Order;
import com.lhjypt.core.model.fund.OrderFundReservation;
import com.lhjypt.core.model.trade.TradeRequest;
import com.lhjypt.core.service.trade.TradeCostCalculator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FundReservationServiceTest {

    @Test
    void reservesFundsOncePerOrder() {
        OrderFundReservationMapper mapper = mock(OrderFundReservationMapper.class);
        FundManagementService fundService = mock(FundManagementService.class);
        FundReservationService service = new FundReservationService(mapper, fundService);

        Order order = order();
        TradeCostCalculator.TradeCost cost = new TradeCostCalculator.TradeCost(
                new BigDecimal("7000"), new BigDecimal("700"), new BigDecimal("2.1"));

        service.reserve(order, new TradeRequest(), cost);

        verify(fundService).freezeCash(
                eq("DEFAULT-001"),
                argThat(value -> value.compareTo(new BigDecimal("702.1")) == 0),
                eq("ORD-001"),
                eq("rb2410"));
        ArgumentCaptor<OrderFundReservation> captor =
                ArgumentCaptor.forClass(OrderFundReservation.class);
        verify(mapper).insert(captor.capture());
        assertEquals("RESERVED", captor.getValue().getStatus());
        assertEquals(0, captor.getValue().getReservedMargin()
                .compareTo(new BigDecimal("700")));
    }

    @Test
    void consumesFillAndSettlesMarginAndCommission() {
        OrderFundReservationMapper mapper = mock(OrderFundReservationMapper.class);
        FundManagementService fundService = mock(FundManagementService.class);
        FundReservationService service = new FundReservationService(mapper, fundService);
        OrderFundReservation reservation = reservation();
        when(mapper.selectByOrderId("ORD-001")).thenReturn(reservation);

        OrderFundReservation result = service.consumeFill(
                "ORD-001", new BigDecimal("700"), new BigDecimal("2.1"));

        verify(fundService).releaseCash(
                "DEFAULT-001", new BigDecimal("2.1"), "ORD-001-COMMISSION", null);
        verify(fundService).settleMarginAndCommission(
                "DEFAULT-001", new BigDecimal("700"), new BigDecimal("2.1"), "ORD-001", null);
        assertEquals("CONSUMED", result.getStatus());
        assertEquals(0, result.getConsumedAmount().compareTo(new BigDecimal("702.1")));
    }

    @Test
    void releasesRemainingFrozenAmountOnOrderTermination() {
        OrderFundReservationMapper mapper = mock(OrderFundReservationMapper.class);
        FundManagementService fundService = mock(FundManagementService.class);
        FundReservationService service = new FundReservationService(mapper, fundService);
        OrderFundReservation reservation = reservation();
        when(mapper.selectByOrderId("ORD-001")).thenReturn(reservation);

        OrderFundReservation result = service.releaseRemaining("ORD-001");

        verify(fundService).releaseCash(
                "DEFAULT-001", new BigDecimal("702.1"), "ORD-001-RELEASE", null);
        assertEquals("RELEASED", result.getStatus());
    }

    private Order order() {
        Order order = new Order();
        order.setOrderId("ORD-001");
        order.setAccountId("DEFAULT-001");
        order.setSymbol("rb2410");
        return order;
    }

    private OrderFundReservation reservation() {
        OrderFundReservation reservation = new OrderFundReservation();
        reservation.setOrderId("ORD-001");
        reservation.setAccountId("DEFAULT-001");
        reservation.setReservedMargin(new BigDecimal("700"));
        reservation.setReservedCommission(new BigDecimal("2.1"));
        reservation.setConsumedMargin(BigDecimal.ZERO);
        reservation.setConsumedCommission(BigDecimal.ZERO);
        reservation.setReleasedAmount(BigDecimal.ZERO);
        reservation.setConsumedAmount(BigDecimal.ZERO);
        reservation.setStatus("RESERVED");
        return reservation;
    }
}
