package com.lhjypt.core.service.trade;

import com.lhjypt.core.mapper.TradeRecordMapper;
import com.lhjypt.core.model.Order;
import com.lhjypt.core.model.trade.TradeRecord;
import com.lhjypt.core.model.trade.TradeReport;
import com.lhjypt.core.service.fund.FundManagementService;
import com.lhjypt.core.service.fund.FundReservationService;
import com.lhjypt.core.service.order.OrderManagementService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeProcessingServiceTest {

    @Test
    void processesTradeAcrossOmsPositionAndFunds() {
        TradeRecordMapper tradeMapper = mock(TradeRecordMapper.class);
        OrderManagementService orderService = mock(OrderManagementService.class);
        PositionService positionService = mock(PositionService.class);
        FundReservationService reservationService = mock(FundReservationService.class);
        FundManagementService fundService = mock(FundManagementService.class);
        TradeCostCalculator calculator = mock(TradeCostCalculator.class);
        TradeProcessingService service = new TradeProcessingService(
                tradeMapper, orderService, positionService,
                reservationService, fundService, calculator);

        Order order = order();
        TradeReport report = report();
        when(tradeMapper.selectByTradeId("T-001")).thenReturn(null);
        when(positionService.applyFill(any(), any())).thenReturn(new BigDecimal("12.5"));
        when(calculator.calculate("OPEN", new BigDecimal("3500"), new BigDecimal("2")))
                .thenReturn(new TradeCostCalculator.TradeCost(
                        new BigDecimal("7000"), new BigDecimal("700"), new BigDecimal("2.1")));

        TradeRecord result = service.process(order, report);

        verify(orderService).applyFill("ORD-001", new BigDecimal("2"), new BigDecimal("3500"));
        verify(reservationService).consumeFill(
                "ORD-001", new BigDecimal("700"), new BigDecimal("2.1"));
        verify(fundService).applyRealizedPnl(
                "DEFAULT-001", new BigDecimal("12.5"), "T-001", "rb2410");
        assertEquals(0, result.getRealizedPnl().compareTo(new BigDecimal("12.5")));
    }

    @Test
    void duplicateTradeReturnsExistingWithoutSideEffects() {
        TradeRecordMapper tradeMapper = mock(TradeRecordMapper.class);
        OrderManagementService orderService = mock(OrderManagementService.class);
        PositionService positionService = mock(PositionService.class);
        FundReservationService reservationService = mock(FundReservationService.class);
        FundManagementService fundService = mock(FundManagementService.class);
        TradeCostCalculator calculator = mock(TradeCostCalculator.class);
        TradeProcessingService service = new TradeProcessingService(
                tradeMapper, orderService, positionService,
                reservationService, fundService, calculator);

        TradeRecord existing = new TradeRecord();
        existing.setTradeId("T-001");
        when(tradeMapper.selectByTradeId("T-001")).thenReturn(existing);

        TradeRecord result = service.process(order(), report());

        assertEquals("T-001", result.getTradeId());
        verify(orderService, never()).applyFill(any(), any(), any());
        verify(positionService, never()).applyFill(any(), any());
    }

    private Order order() {
        Order order = new Order();
        order.setOrderId("ORD-001");
        order.setAccountId("DEFAULT-001");
        order.setStrategyId("S-001");
        order.setSymbol("rb2410");
        return order;
    }

    private TradeReport report() {
        TradeReport report = new TradeReport();
        report.setTradeId("T-001");
        report.setOrderRef("000001");
        report.setClientOrderId("ORD-001");
        report.setExchangeOrderId("EX000001");
        report.setSymbol("rb2410");
        report.setExchange("SHFE");
        report.setDirection("BUY");
        report.setOffsetFlag("OPEN");
        report.setPrice(new BigDecimal("3500"));
        report.setVolume(new BigDecimal("2"));
        report.setTurnover(new BigDecimal("7000"));
        report.setTradingDay("20261005");
        report.setTradeTime(java.time.LocalDateTime.now());
        return report;
    }
}
