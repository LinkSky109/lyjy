package com.lhjypt.core.service.trade.impl;

import com.lhjypt.core.model.trade.CTPConfig;
import com.lhjypt.core.model.trade.PositionDetail;
import com.lhjypt.core.model.trade.TradeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Mock 网关市价单价格兜底行为测试（复现 P1 事中风控强平缺陷）
 */
class MockCTPGatewayMarketPriceTest {

    private MockCTPGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new MockCTPGateway();
        CTPConfig config = new CTPConfig();
        config.setBrokerId("9999");
        config.setUserId("test001");
        config.setPassword("test123");
        gateway.connect(config);
    }

    private TradeRequest req(String symbol, String direction, String offsetFlag,
                             String orderType, BigDecimal price, int qty) {
        TradeRequest r = new TradeRequest();
        r.setSymbol(symbol);
        r.setExchange("SHFE");
        r.setDirection(direction);
        r.setOffsetFlag(offsetFlag);
        r.setOrderType(orderType);
        r.setPrice(price);
        r.setQuantity(new BigDecimal(qty));
        r.setAccountId("test001");
        return r;
    }

    @Test
    @DisplayName("市价平仓单不带价格应正常成交（修复强平0笔缺陷），持仓清零")
    void marketCloseWithoutPriceShouldFillAtFallback() {
        gateway.submitOrder(req("rb2410", "BUY", "OPEN", "LIMIT", new BigDecimal("3500"), 60));
        assertThat(positionVolume("rb2410", "LONG")).isEqualByComparingTo("60");

        assertThatCode(() -> gateway.submitOrder(
                req("rb2410", "SELL", "CLOSE", "MARKET", null, 60)))
                .doesNotThrowAnyException();

        assertThat(positionVolume("rb2410", "LONG")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("无任何持仓时市价开仓应能以参考兜底价成交")
    void marketOpenWithoutAnyPositionShouldFill() {
        assertThatCode(() -> gateway.submitOrder(
                req("hc2410", "BUY", "OPEN", "MARKET", null, 10)))
                .doesNotThrowAnyException();
        assertThat(positionVolume("hc2410", "LONG")).isEqualByComparingTo("10");
    }

    private BigDecimal positionVolume(String symbol, String direction) {
        List<PositionDetail> ps = gateway.queryPositions();
        return ps.stream()
                .filter(p -> p.getSymbol().equals(symbol) && p.getDirection().equals(direction))
                .map(PositionDetail::getTotalVolume)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }
}
