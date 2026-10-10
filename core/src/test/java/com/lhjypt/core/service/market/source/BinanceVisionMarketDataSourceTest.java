package com.lhjypt.core.service.market.source;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BinanceVisionMarketDataSourceTest {

    @Test
    @DisplayName("buildUrl：分页游标 endTime 仅在提供时附带（毫秒，P4-001）")
    void buildUrlAddsEndTimeOnlyWhenPresent() {
        String noCursor = BinanceVisionMarketDataSource.buildUrl("BTCUSDT", "1h", 1000, null);
        assertThat(noCursor)
                .contains("symbol=BTCUSDT")
                .contains("interval=1h")
                .contains("limit=1000")
                .doesNotContain("endTime");

        String withCursor = BinanceVisionMarketDataSource.buildUrl("BTCUSDT", "1h", 500, 1700000000L);
        assertThat(withCursor)
                .contains("limit=500")
                .contains("endTime=1700000000000");
    }

    @Test
    @DisplayName("解析 Binance klines（openTime 毫秒 -> 秒）")
    void parsesKlines() {
        String body = "[[1700000000000,\"100\",\"120\",\"90\",\"110\",\"5\","
                + "1700003599999,\"550\",1,\"0\",\"0\",\"0\"]]";

        List<MarketCandle> candles = BinanceVisionMarketDataSource.parse(body);

        assertThat(candles).hasSize(1);
        MarketCandle candle = candles.get(0);
        assertThat(candle.timestamp()).isEqualTo(1700000000L);
        assertThat(candle.open()).isEqualTo("100");
        assertThat(candle.high()).isEqualTo("120");
        assertThat(candle.low()).isEqualTo("90");
        assertThat(candle.close()).isEqualTo("110");
        assertThat(candle.volume()).isEqualTo("5");
        assertThat(candle.amount()).isEqualTo("550");
    }
}
