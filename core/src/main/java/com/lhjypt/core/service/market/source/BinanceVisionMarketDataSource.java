package com.lhjypt.core.service.market.source;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.lhjypt.core.config.RealMarketProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Binance 公开数据源（备用源，P3-006）：{@code data-api.binance.vision}，免密钥、响应快。
 * 响应：{@code [[openTimeMs, open, high, low, close, volume, closeTime, quoteVolume, ...]]}
 */
@Component
public class BinanceVisionMarketDataSource implements MarketDataSource {

    private static final String BASE = "https://data-api.binance.vision/api/v3/klines";

    private final RealMarketProperties properties;
    private final HttpClient httpClient;

    public BinanceVisionMarketDataSource(RealMarketProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
    }

    @Override
    public String name() {
        return "binance-vision";
    }

    @Override
    public List<MarketCandle> fetch(String pair, String interval, int limit) throws Exception {
        return fetch(pair, interval, limit, null);
    }

    @Override
    public boolean supportsBackfill() {
        return true;
    }

    @Override
    public List<MarketCandle> fetch(String pair, String interval, int limit, Long endTimeSec)
            throws Exception {
        String symbol = pair.replace("_", "").toUpperCase(Locale.ROOT); // BTC_USDT -> BTCUSDT
        String url = buildUrl(symbol, interval, limit, endTimeSec);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("binance-vision 返回 " + response.statusCode());
        }
        return parse(response.body());
    }

    /** 组装 Binance klines 请求 URL（分页回补时附带 {@code endTime} 游标，毫秒）。 */
    static String buildUrl(String symbol, String interval, int limit, Long endTimeSec) {
        StringBuilder url = new StringBuilder(BASE)
                .append("?symbol=").append(symbol)
                .append("&interval=").append(interval)
                .append("&limit=").append(limit);
        if (endTimeSec != null && endTimeSec > 0) {
            url.append("&endTime=").append(endTimeSec * 1000L);
        }
        return url.toString();
    }

    static List<MarketCandle> parse(String body) {
        JSONArray array = JSON.parseArray(body);
        List<MarketCandle> candles = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JSONArray item = array.getJSONArray(i);
            long tsMs = item.getLongValue(0);
            candles.add(new MarketCandle(
                    tsMs / 1000,
                    item.getString(1),  // open
                    item.getString(2),  // high
                    item.getString(3),  // low
                    item.getString(4),  // close
                    item.getString(5),  // volume
                    item.getString(7))); // quoteVolume
        }
        return candles;
    }
}