package com.lhjypt.core.service.trade.impl;

import com.lhjypt.core.model.trade.CTPConfig;
import com.lhjypt.core.model.trade.OrderReport;
import com.lhjypt.core.model.trade.TradeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MockCTPGatewayHoldTest {

    private MockCTPGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new MockCTPGateway();
        ReflectionTestUtils.setField(gateway, "mockFillEnabled", false);
        CTPConfig config = new CTPConfig();
        config.setBrokerId("9999");
        config.setUserId("test001");
        config.setPassword("test123");
        gateway.connect(config);
    }

    @Test
    @DisplayName("关闭自动成交后订单保持待成交并可撤单")
    void holdsOrderAndCancelsSuccessfully() {
        TradeRequest request = new TradeRequest();
        request.setSymbol("rb2410");
        request.setExchange("SHFE");
        request.setDirection("BUY");
        request.setOffsetFlag("OPEN");
        request.setOrderType("LIMIT");
        request.setPrice(new BigDecimal("3500"));
        request.setQuantity(BigDecimal.ONE);
        request.setAccountId("test001");

        String orderRef = gateway.submitOrder(request);
        String exchangeOrderId = "EX" + orderRef;

        OrderReport report = gateway.queryOrder(exchangeOrderId);
        assertThat(report).isNotNull();
        assertThat(report.getOrderStatus()).isEqualTo("NOT_TRADED");

        assertThat(gateway.cancelOrder(exchangeOrderId, "rb2410")).isTrue();
        assertThat(gateway.queryOrder(exchangeOrderId).getOrderStatus()).isEqualTo("CANCELED");
    }
}
