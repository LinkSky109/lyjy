package com.lhjypt.core.service.market.source;

import com.lhjypt.core.service.market.impl.real.GateioKlineSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Gate.io 行情源实现（主源，P3-006）。
 * 注意：Gate 响应可能较慢（实测 16s+），超时默认已提高（见 lhjypt.market.real.timeout-seconds）。
 */
@Component
public class GateioMarketDataSource implements MarketDataSource {

    private final GateioKlineSource gateioKlineSource;

    public GateioMarketDataSource(GateioKlineSource gateioKlineSource) {
        this.gateioKlineSource = gateioKlineSource;
    }

    @Override
    public String name() {
        return "gateio";
    }

    @Override
    public List<MarketCandle> fetch(String pair, String interval, int limit) throws Exception {
        return toCandles(gateioKlineSource.fetchCandles(pair, interval, limit, null));
    }

    @Override
    public boolean supportsBackfill() {
        return true;
    }

    @Override
    public List<MarketCandle> fetch(String pair, String interval, int limit, Long endTimeSec)
            throws Exception {
        return toCandles(gateioKlineSource.fetchCandles(pair, interval, limit, endTimeSec));
    }

    private static List<MarketCandle> toCandles(List<GateioKlineSource.RealKline> candles) {
        List<MarketCandle> result = new ArrayList<>(candles.size());
        for (GateioKlineSource.RealKline candle : candles) {
            result.add(new MarketCandle(candle.timestamp(), candle.open(), candle.high(),
                    candle.low(), candle.close(), candle.volume(), candle.amount()));
        }
        return result;
    }
}