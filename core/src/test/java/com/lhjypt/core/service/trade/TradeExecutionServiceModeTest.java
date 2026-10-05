package com.lhjypt.core.service.trade;

import com.lhjypt.core.model.trade.CTPConfig;
import com.lhjypt.core.service.trade.impl.CTPGatewayAdapter;
import com.lhjypt.core.service.trade.impl.MockCTPGateway;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeExecutionServiceModeTest {

    @Test
    void liveModeFailsFastWhenRealAdapterIsUnavailable() {
        TradeExecutionService service = new TradeExecutionService(
                new MockCTPGateway(), new CTPGatewayAdapter());
        CTPConfig config = new CTPConfig();
        config.setMode("LIVE");
        config.setUserId("trader-001");

        IllegalStateException error = assertThrows(
                IllegalStateException.class, () -> service.connect(config));

        assertTrue(error.getMessage().contains("LIVE"));
        assertTrue(error.getMessage().contains("禁止回退"));
    }

    @Test
    void mockModeConnectsAndReportsMode() {
        TradeExecutionService service = new TradeExecutionService(
                new MockCTPGateway(), new CTPGatewayAdapter());
        CTPConfig config = new CTPConfig();
        config.setMode("MOCK");
        config.setUserId("trader-001");

        service.connect(config);

        assertTrue(service.isConnected());
        assertTrue(service.isAuthenticated());
        assertEquals("MOCK", service.getMode());
    }

    @Test
    void unknownModeIsRejected() {
        TradeExecutionService service = new TradeExecutionService(
                new MockCTPGateway(), new CTPGatewayAdapter());
        CTPConfig config = new CTPConfig();
        config.setMode("PAPER");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> service.connect(config));

        assertTrue(error.getMessage().contains("未知交易运行模式"));
    }
}
