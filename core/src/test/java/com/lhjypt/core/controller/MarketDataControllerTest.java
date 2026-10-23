package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.model.market.TickData;
import com.lhjypt.core.service.market.MarketDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketDataControllerTest {

    private final MarketDataService marketDataService = mock(MarketDataService.class);
    private final MarketDataController controller = new MarketDataController(marketDataService);

    @Test
    @DisplayName("有 tick 时返回快照与 staleMs")
    void quoteReturnsSnapshot() {
        TickData tick = new TickData();
        tick.setSymbol("rb2410");
        tick.setLastPrice(new BigDecimal("3500"));
        tick.setPreClose(new BigDecimal("3480"));
        tick.setBidPrice1(new BigDecimal("3499"));
        tick.setBidVolume1(12L);
        tick.setAskPrice1(new BigDecimal("3501"));
        tick.setAskVolume1(8L);
        tick.setRecvTime(LocalDateTime.now().minusSeconds(2));
        when(marketDataService.getLatestTick("rb2410")).thenReturn(tick);

        Result<Map<String, Object>> result = controller.quote("rb2410");

        assertThat(result.getCode()).isZero();
        Map<String, Object> data = result.getData();
        assertThat(data.get("symbol")).isEqualTo("rb2410");
        assertThat((BigDecimal) data.get("lastPrice")).isEqualByComparingTo("3500");
        assertThat((BigDecimal) data.get("bidPrice1")).isEqualByComparingTo("3499");
        assertThat((Long) data.get("askVolume1")).isEqualTo(8L);
        assertThat((Long) data.get("staleMs")).isGreaterThanOrEqualTo(2000L);
    }

    @Test
    @DisplayName("无 tick 时返回 data=null")
    void quoteReturnsNullWhenNoTick() {
        when(marketDataService.getLatestTick("missing")).thenReturn(null);

        Result<Map<String, Object>> result = controller.quote("missing");

        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).isNull();
    }
}
