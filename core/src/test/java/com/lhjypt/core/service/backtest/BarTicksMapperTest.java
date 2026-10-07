package com.lhjypt.core.service.backtest;

import com.lhjypt.core.model.market.BarData;
import com.lhjypt.core.model.market.TickData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BarTicksMapperTest {

    @Test
    @DisplayName("真实 K 线 → 4 个 OHLC 参考 tick：价格落位、时间有序、量均分")
    void mapsBarToTicks() {
        BarData bar = new BarData();
        bar.setSymbol("BTC_USDT");
        bar.setOpen(new BigDecimal("84000"));
        bar.setHigh(new BigDecimal("85000"));
        bar.setLow(new BigDecimal("83800"));
        bar.setClose(new BigDecimal("84500"));
        bar.setVolume(400);
        bar.setTurnover(new BigDecimal("40000000"));
        bar.setBarTime(LocalDateTime.of(2026, 10, 7, 12, 0));

        List<TickData> ticks = BarTicksMapper.fromBars(List.of(bar));

        assertThat(ticks).hasSize(4);
        assertThat(ticks.get(0).getLastPrice()).isEqualByComparingTo("84000"); // O
        assertThat(ticks.get(1).getLastPrice()).isEqualByComparingTo("85000"); // H
        assertThat(ticks.get(2).getLastPrice()).isEqualByComparingTo("83800"); // L
        assertThat(ticks.get(3).getLastPrice()).isEqualByComparingTo("84500"); // C
        for (TickData tick : ticks) {
            assertThat(tick.getSymbol()).isEqualTo("BTC_USDT");
            assertThat(tick.getVolume()).isEqualTo(100L); // 400/4
            assertThat(tick.getTurnover()).isEqualByComparingTo("10000000"); // 4e7/4
        }
        assertThat(ticks.get(3).getTickTime()).isAfter(ticks.get(0).getTickTime());
    }

    @Test
    @DisplayName("收盘信号模式：每根 bar 仅 1 个收盘价 tick")
    void mapsBarToCloseTick() {
        BarData bar = new BarData();
        bar.setSymbol("BTC_USDT");
        bar.setOpen(new BigDecimal("84000"));
        bar.setHigh(new BigDecimal("85000"));
        bar.setLow(new BigDecimal("83800"));
        bar.setClose(new BigDecimal("84500"));
        bar.setVolume(400);
        bar.setTurnover(new BigDecimal("40000000"));
        bar.setBarTime(LocalDateTime.of(2026, 10, 7, 12, 0));

        List<TickData> ticks = BarTicksMapper.fromBars(List.of(bar), BarTicksMapper.MODE_CLOSE);

        assertThat(ticks).hasSize(1);
        assertThat(ticks.get(0).getLastPrice()).isEqualByComparingTo("84500");
        assertThat(ticks.get(0).getVolume()).isEqualTo(400L);
        assertThat(ticks.get(0).getTurnover()).isEqualByComparingTo("10000000");
    }

    @Test
    @DisplayName("乱序输入（ClickHouse DESC）→ 输出按时间升序")
    void sortsByTimeAscending() {
        BarData later = new BarData();
        later.setSymbol("BTC_USDT");
        later.setFrequency("1h");
        later.setOpen(new BigDecimal("101"));
        later.setHigh(new BigDecimal("102"));
        later.setLow(new BigDecimal("100"));
        later.setClose(new BigDecimal("101"));
        later.setVolume(1);
        later.setBarTime(LocalDateTime.of(2026, 10, 7, 12, 0));

        BarData earlier = new BarData();
        earlier.setSymbol("BTC_USDT");
        earlier.setFrequency("1h");
        earlier.setOpen(new BigDecimal("100"));
        earlier.setHigh(new BigDecimal("101"));
        earlier.setLow(new BigDecimal("99"));
        earlier.setClose(new BigDecimal("100"));
        earlier.setVolume(1);
        earlier.setBarTime(LocalDateTime.of(2026, 10, 7, 11, 0));

        List<TickData> ticks = BarTicksMapper.fromBars(
                List.of(later, earlier), BarTicksMapper.MODE_CLOSE);

        assertThat(ticks).hasSize(2);
        assertThat(ticks.get(0).getTickTime()).isBefore(ticks.get(1).getTickTime());
        assertThat(ticks.get(0).getLastPrice()).isEqualByComparingTo("100");
        assertThat(ticks.get(1).getLastPrice()).isEqualByComparingTo("101");
    }

    @Test
    @DisplayName("空输入返回空序列")
    void emptyInput() {
        assertThat(BarTicksMapper.fromBars(List.of())).isEmpty();
        assertThat(BarTicksMapper.fromBars(null)).isEmpty();
        assertThat(BarTicksMapper.fromBars(List.of(), "close")).isEmpty();
    }
}