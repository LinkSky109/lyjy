package com.lhjypt.core.service.backtest;

import com.lhjypt.core.model.market.BarData;
import com.lhjypt.core.model.market.TickData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 真实 K 线（ClickHouse bar_data）转 tick 序列，供回测引擎按真实行情撮合。
 *
 * <p>两种信号模式：
 * <ul>
 *   <li>{@link #MODE_OHLC}（默认）：每根 bar 展开 4 个参考 tick（Open→High→Low→Close），
 *       能在 bar 内看到高低价波动；</li>
 *   <li>{@link #MODE_CLOSE}：每根 bar 仅 1 个收盘价 tick，贴近“收盘信号”类策略
 *       （如均线交叉在收盘确认），避免 bar 内 O/H/L 顺序伪影。</li>
 * </ul>
 */
public final class BarTicksMapper {

    public static final String MODE_OHLC = "ohlc";
    public static final String MODE_CLOSE = "close";

    private BarTicksMapper() {
    }

    public static List<TickData> fromBars(List<BarData> bars) {
        return fromBars(bars, MODE_OHLC);
    }

    public static List<TickData> fromBars(List<BarData> bars, String mode) {
        if (bars == null || bars.isEmpty()) {
            return new ArrayList<>();
        }
        // ClickHouse 查询按时间倒序返回；回测/优化必须按时间升序，否则等于使用未来数据
        List<BarData> ordered = new ArrayList<>(bars);
        ordered.sort(Comparator.comparing(BarData::getBarTime,
                Comparator.nullsLast(Comparator.naturalOrder())));
        List<TickData> ticks = new ArrayList<>();
        boolean closeOnly = MODE_CLOSE.equalsIgnoreCase(mode);
        for (BarData bar : ordered) {
            if (closeOnly) {
                ticks.add(tick(bar, bar.getClose(), bar.getBarTime(), bar.getVolume()));
            } else {
                long volumePerTick = Math.max(1L, bar.getVolume() / 4);
                ticks.add(tick(bar, bar.getOpen(), barTime(bar, 0), volumePerTick));
                ticks.add(tick(bar, bar.getHigh(), barTime(bar, 1), volumePerTick));
                ticks.add(tick(bar, bar.getLow(), barTime(bar, 2), volumePerTick));
                ticks.add(tick(bar, bar.getClose(), barTime(bar, 3), volumePerTick));
            }
        }
        return ticks;
    }

    private static TickData tick(BarData bar, BigDecimal lastPrice,
                                 LocalDateTime time, long volume) {
        TickData tick = new TickData();
        tick.setSymbol(bar.getSymbol());
        tick.setLastPrice(lastPrice);
        tick.setOpen(bar.getOpen());
        tick.setHigh(bar.getHigh());
        tick.setLow(bar.getLow());
        tick.setVolume(volume);
        BigDecimal turnover = bar.getTurnover() == null ? BigDecimal.ZERO : bar.getTurnover();
        tick.setTurnover(turnover.divide(BigDecimal.valueOf(4), RoundingMode.HALF_UP));
        tick.setTickTime(time);
        tick.setRecvTime(time);
        return tick;
    }

    private static LocalDateTime barTime(BarData bar, int sub) {
        LocalDateTime base = bar.getBarTime();
        return base == null ? LocalDateTime.now() : base.plusNanos(sub * 1_000_000L);
    }
}