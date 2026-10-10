package com.lhjypt.core.service.market.impl.real;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GateioKlineSourceTest {

    @Test
    @DisplayName("解析 Gate.io K 线响应（真实格式）")
    void parsesCandles() {
        String body = "["
                + "[\"1791338400\",\"124544690.77587450\",\"83862.5\",\"84535.7\",\"83584.9\",\"84390\",\"1484.17091000\",true],"
                + "[\"1791342000\",\"42038512.76828760\",\"84155\",\"84247\",\"83621\",\"84247\",\"499.55835200\",true]"
                + "]";

        List<GateioKlineSource.RealKline> candles = GateioKlineSource.parseCandles(body);

        assertThat(candles).hasSize(2);
        GateioKlineSource.RealKline first = candles.get(0);
        assertThat(first.timestamp()).isEqualTo(1791338400L);
        assertThat(first.open()).isEqualTo("83862.5");
        assertThat(first.high()).isEqualTo("84535.7");
        assertThat(first.close()).isEqualTo("84390");
        assertThat(first.volume()).isEqualTo("1484.17091000");
    }

    @Test
    @DisplayName("非法响应抛异常而非静默")
    void rejectsMalformedBody() {
        assertThatThrownBy(() -> GateioKlineSource.parseCandles("not-json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("解析失败");
    }

    @Test
    @DisplayName("buildUrl：分页游标 to 仅在提供时附带（P4-001）")
    void buildUrlAddsCursorOnlyWhenPresent() {
        String base = "https://api.gateio.ws/api/v4/spot/candlesticks";
        assertThat(GateioKlineSource.buildUrl(base, "BTC_USDT", "1h", 1000, null))
                .isEqualTo(base + "?currency_pair=BTC_USDT&interval=1h&limit=1000");
        assertThat(GateioKlineSource.buildUrl(base, "BTC_USDT", "1h", 500, 1700000000L))
                .isEqualTo(base + "?currency_pair=BTC_USDT&interval=1h&limit=500&to=1700000000");
    }
}