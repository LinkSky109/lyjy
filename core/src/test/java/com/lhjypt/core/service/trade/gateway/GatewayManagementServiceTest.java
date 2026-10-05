package com.lhjypt.core.service.trade.gateway;

import com.lhjypt.core.mapper.GatewayInfoMapper;
import com.lhjypt.core.model.gateway.GatewayInfo;
import com.lhjypt.core.service.trade.TradingGateway;
import com.lhjypt.core.service.trade.impl.CTPGatewayAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayManagementServiceTest {

    @Test
    void registersMockGatewayIntoRouterAndDatabase() {
        GatewayInfoMapper mapper = mock(GatewayInfoMapper.class);
        GatewayRouter router = mock(GatewayRouter.class);
        GatewayManagementService service = new GatewayManagementService(
                mapper, router, mock(CTPGatewayAdapter.class));
        GatewayInfo info = info("mock-2", "MOCK");
        when(mapper.selectByGatewayId("mock-2")).thenReturn(null);

        service.register(info);

        verify(mapper).insert(info);
        verify(router).register(eq(info), any(TradingGateway.class));
    }

    @Test
    void rejectsUnsupportedGatewayType() {
        GatewayManagementService service = new GatewayManagementService(
                mock(GatewayInfoMapper.class), mock(GatewayRouter.class),
                mock(CTPGatewayAdapter.class));
        GatewayInfo info = info("fix-1", "FIX");

        assertThrows(IllegalArgumentException.class, () -> service.register(info));
    }

    private static GatewayInfo info(String id, String type) {
        GatewayInfo info = new GatewayInfo();
        info.setGatewayId(id);
        info.setGatewayType(type);
        info.setName(id);
        return info;
    }
}
