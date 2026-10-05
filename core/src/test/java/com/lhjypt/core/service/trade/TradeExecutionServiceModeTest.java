package com.lhjypt.core.service.trade;

import com.lhjypt.core.mapper.AlertEventMapper;
import com.lhjypt.core.mapper.GatewayInfoMapper;
import com.lhjypt.core.mapper.GatewayRouteRuleMapper;
import com.lhjypt.core.model.trade.CTPConfig;
import com.lhjypt.core.service.trade.gateway.GatewayRegistry;
import com.lhjypt.core.service.trade.gateway.GatewayRouteService;
import com.lhjypt.core.service.trade.gateway.GatewayRouter;
import com.lhjypt.core.service.trade.impl.CTPGatewayAdapter;
import com.lhjypt.core.service.trade.impl.MockCTPGateway;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradeExecutionServiceModeTest {

    @Test
    void liveModeFailsFastWhenRealAdapterIsUnavailable() {
        TradeExecutionService service = service();
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
        TradeExecutionService service = service();
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
        TradeExecutionService service = service();
        CTPConfig config = new CTPConfig();
        config.setMode("PAPER");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> service.connect(config));

        assertTrue(error.getMessage().contains("未知交易运行模式"));
    }

    @SuppressWarnings("unchecked")
    private TradeExecutionService service() {
        GatewayInfoMapper infoMapper = mock(GatewayInfoMapper.class);
        GatewayRouteRuleMapper routeMapper = mock(GatewayRouteRuleMapper.class);
        when(routeMapper.selectEnabledRules()).thenReturn(List.of());
        GatewayRegistry registry = new GatewayRegistry(
                infoMapper, mock(AlertEventMapper.class));
        GatewayRouteService routeService = new GatewayRouteService(routeMapper);
        GatewayRouter router = new GatewayRouter(
                registry, routeService, mock(KafkaTemplate.class));
        TradeExecutionService service = new TradeExecutionService(
                new MockCTPGateway(), new CTPGatewayAdapter(), router);
        service.initializeDefaultGateway();
        return service;
    }
}
