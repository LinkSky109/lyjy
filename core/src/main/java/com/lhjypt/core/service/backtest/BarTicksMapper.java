package com.lhjypt.core.service.backtest;

import com.lhjypt.core.model.market.BarData;
import com.lhjypt.core.model.market.TickData;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 真实 K 线（ClickHouse bar_data）转 tick 序列，供回测引擎按真实行情撮合。
 * 每根 bar 展开为 4 个参考 tick（Open→High→Low→Close），成交量均分；
 * tick 时间取 bar 时间（barTime），OHLC 内按毫秒递增保持顺序。
 */
public final class BarTicksMapper {

    private BarTicksMapper() {
    }

    public static List<TickData> fromBars(List<BarData> bars) {
        List<TickData> ticks = new ArrayList<>();
        if (bars == null || bars.isEmpty()) {
            return ticks;
        }
        for (BarData bar : bars) {
            long volumePerTick = Math.max(1L, bar.getVolume() / 4);
            ticks.add(tick(bar, bar.getOpen(), barTime(bar, 0), volumePerTick));
            ticks.add(tick(bar, bar.getHigh(), barTime(bar, 1), volumePerTick));
            ticks.add(tick(bar, bar.getLow(), barTime(bar, 2), volumePerTick));
            ticks.add(tick(bar, bar.getClose(), barTime(bar, 3), volumePerTick));
        }
        return ticks;
    }

    private static TickData tick(BarData bar, java.math.BigDecimal lastPrice,
                                 LocalDateTime time, long volume) {
        TickData tick = new TickData();
        tick.setSymbol(bar.getSymbol());
        tick.setLastPrice(lastPrice);
        tick.setOpen(bar.getOpen());
        tick.setHigh(bar.getHigh());
        tick.setLow(bar.getLow());
        tick.setVolume(volume);
        tick.setTurnover(bar.getTurnover() == null ? java.math.BigDecimal.ZERO
                : bar.getTurnover().divide(java.math.BigDecimal.valueOf(4),
                java.math.RoundingMode.HALF_UP));
        tick.setTickTime(time);
        tick.setRecvTime(time);
        return tick;
    }

    private static LocalDateTime barTime(BarData bar, int sub) {
        LocalDateTime base = bar.getBarTime();
        return base == null ? LocalDateTime.now() : base.plusNanos(sub * 1_000_000L);
    }
}