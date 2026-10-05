package com.lhjypt.core.service.trade;

import com.lhjypt.core.model.trade.OrderReport;
import com.lhjypt.core.model.trade.TradeReport;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GatewayReportProcessorTest {

    @Test
    void registersAndDelegatesReports() {
        TradeExecutionService executionService = mock(TradeExecutionService.class);
        GatewayReportHandlingService handlingService =
                mock(GatewayReportHandlingService.class);
        GatewayReportProcessor processor =
                new GatewayReportProcessor(executionService, handlingService);

        processor.register();
        processor.onOrderReport(new OrderReport());
        processor.onTradeReport(new TradeReport());

        verify(executionService).registerReportListeners(processor, processor);
        verify(handlingService).handleOrderReport(org.mockito.ArgumentMatchers.any());
        verify(handlingService).handleTradeReport(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reportHandlingFailureDoesNotEscapeGatewayThread() {
        TradeExecutionService executionService = mock(TradeExecutionService.class);
        GatewayReportHandlingService handlingService =
                mock(GatewayReportHandlingService.class);
        GatewayReportProcessor processor =
                new GatewayReportProcessor(executionService, handlingService);
        doThrow(new IllegalStateException("boom"))
                .when(handlingService).handleOrderReport(org.mockito.ArgumentMatchers.any());

        processor.onOrderReport(new OrderReport());
    }
}
