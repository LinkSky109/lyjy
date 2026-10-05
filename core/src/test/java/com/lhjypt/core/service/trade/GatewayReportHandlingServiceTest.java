package com.lhjypt.core.service.trade;

import com.lhjypt.core.mapper.GatewayReportLogMapper;
import com.lhjypt.core.model.Order;
import com.lhjypt.core.model.trade.GatewayReportLog;
import com.lhjypt.core.model.trade.OrderReport;
import com.lhjypt.core.model.trade.TradeReport;
import com.lhjypt.core.service.order.OrderManagementService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayReportHandlingServiceTest {

    @Test
    void ignoresDuplicateReport() {
        GatewayReportLogMapper logMapper = mock(GatewayReportLogMapper.class);
        OrderManagementService orderService = mock(OrderManagementService.class);
        TradeProcessingService tradeService = mock(TradeProcessingService.class);
        GatewayReportHandlingService service = new GatewayReportHandlingService(
                logMapper, orderService, tradeService);
        when(logMapper.selectByReportKey(anyString(), anyString(), anyString()))
                .thenReturn(new GatewayReportLog());

        service.handleOrderReport(orderReport());

        verify(orderService, never()).applyOrderReport(any());
    }

    @Test
    void appliesOrderReportAndMarksProcessed() {
        GatewayReportLogMapper logMapper = mock(GatewayReportLogMapper.class);
        OrderManagementService orderService = mock(OrderManagementService.class);
        TradeProcessingService tradeService = mock(TradeProcessingService.class);
        GatewayReportHandlingService service = new GatewayReportHandlingService(
                logMapper, orderService, tradeService);
        when(logMapper.selectByReportKey("ACTIVE", "ORDER", "000001|EX000001|NOT_TRADED|0"))
                .thenReturn(null);

        service.handleOrderReport(orderReport());

        verify(orderService).applyOrderReport(any(OrderReport.class));
        verify(logMapper).updateById(any(GatewayReportLog.class));
    }

    @Test
    void processesTradeReportForKnownOrder() {
        GatewayReportLogMapper logMapper = mock(GatewayReportLogMapper.class);
        OrderManagementService orderService = mock(OrderManagementService.class);
        TradeProcessingService tradeService = mock(TradeProcessingService.class);
        GatewayReportHandlingService service = new GatewayReportHandlingService(
                logMapper, orderService, tradeService);
        when(logMapper.selectByReportKey("ACTIVE", "TRADE", "T-001")).thenReturn(null);
        Order order = new Order();
        order.setOrderId("ORD-001");
        when(orderService.findByClientOrderIdOrBrokerRef("ORD-001", "000001"))
                .thenReturn(order);

        service.handleTradeReport(tradeReport());

        verify(tradeService).process(any(Order.class), any(TradeReport.class));
    }

    @Test
    void rejectsTradeReportWhenOrderCannotBeMatched() {
        GatewayReportLogMapper logMapper = mock(GatewayReportLogMapper.class);
        OrderManagementService orderService = mock(OrderManagementService.class);
        TradeProcessingService tradeService = mock(TradeProcessingService.class);
        GatewayReportHandlingService service = new GatewayReportHandlingService(
                logMapper, orderService, tradeService);
        when(logMapper.selectByReportKey("ACTIVE", "TRADE", "T-001")).thenReturn(null);
        when(orderService.findByClientOrderIdOrBrokerRef("ORD-001", "000001"))
                .thenReturn(null);

        assertThrows(IllegalStateException.class,
                () -> service.handleTradeReport(tradeReport()));
        verify(tradeService, never()).process(any(), any());
    }

    private OrderReport orderReport() {
        OrderReport report = new OrderReport();
        report.setOrderRef("000001");
        report.setClientOrderId("ORD-001");
        report.setExchangeOrderId("EX000001");
        report.setOrderStatus("NOT_TRADED");
        report.setVolumeTraded(BigDecimal.ZERO);
        return report;
    }

    private TradeReport tradeReport() {
        TradeReport report = new TradeReport();
        report.setTradeId("T-001");
        report.setOrderRef("000001");
        report.setClientOrderId("ORD-001");
        report.setSymbol("rb2410");
        report.setDirection("BUY");
        report.setOffsetFlag("OPEN");
        report.setPrice(new BigDecimal("3500"));
        report.setVolume(new BigDecimal("2"));
        report.setTurnover(new BigDecimal("7000"));
        return report;
    }
}
