package com.lhjypt.core.service.market.impl.real;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.lhjypt.core.config.RealMarketProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Gate.io 现货 K 线拉取源（OBS-005 真实行情）。公开 API，免密钥。
 *
 * <p>GET {@code /api/v4/spot/candlesticks?currency_pair=BTC_USDT&interval=1h&limit=N}
 * 返回 JSON 数组，每根：{@code [ts, amount, open, high, low, close, volume, finished]}。
 */
@Component
public class GateioKlineSource {

    private static final Logger log = LoggerFactory.getLogger(GateioKlineSource.class);

    private final RealMarketProperties properties;
    private final HttpClient httpClient;

    public GateioKlineSource(RealMarketProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
    }

    public List<RealKline> fetchCandles(String currencyPair, String interval, int limit)
            throws IOException, InterruptedException {
        return fetchCandles(currencyPair, interval, limit, null);
    }

    /**
     * 分页拉取（P4-001）：{@code toSeconds} 为结束时间游标（秒，含），为空取最近 {@code limit} 根。
     * Gate v4 现货 candlesticks 支持 {@code to} 参数用于历史回补。
     */
    public List<RealKline> fetchCandles(String currencyPair, String interval, int limit,
                                        Long toSeconds) throws IOException, InterruptedException {
        String url = buildUrl(properties.getBaseUrl(), currencyPair, interval, limit, toSeconds);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Gate.io 返回 " + response.statusCode() + ": "
                    + response.body().substring(0, Math.min(response.body().length(), 160)));
        }
        List<RealKline> candles = parseCandles(response.body());
        if (candles.isEmpty()) {
            throw new IllegalStateException("Gate.io 无行情数据: " + currencyPair + "@" + interval);
        }
        return candles;
    }

    /** 组装 Gate.io candlesticks 请求 URL（分页回补时附带 {@code to} 游标）。 */
    static String buildUrl(String baseUrl, String currencyPair, String interval,
                           int limit, Long toSeconds) {
        StringBuilder url = new StringBuilder(baseUrl)
                .append("?currency_pair=").append(currencyPair)
                .append("&interval=").append(interval)
                .append("&limit=").append(limit);
        if (toSeconds != null && toSeconds > 0) {
            url.append("&to=").append(toSeconds);
        }
        return url.toString();
    }

    static List<RealKline> parseCandles(String body) {        List<RealKline> result = new ArrayList<>();
        try {
            JSONArray array = JSON.parseArray(body);
            for (int i = 0; i < array.size(); i++) {
                JSONArray item = array.getJSONArray(i);
                result.add(new RealKline(
                        item.getLong(0),          // 时间戳（秒）
                        item.getString(1),        // 成交额
                        item.getString(2),        // 开盘
                        item.getString(3),        // 最高
                        item.getString(4),        // 最低
                        item.getString(5),        // 收盘
                        item.getString(6)));      // 成交量
            }
        } catch (JSONException error) {
            throw new IllegalStateException("Gate.io 响应解析失败: " + error.getMessage());
        }
        return result;
    }

    /** 解析后的单根 K 线（原始字符串字段，由服务层转 BigDecimal）。 */
    public record RealKline(long timestamp, String amount, String open,
                            String high, String low, String close, String volume) {

        public static RealKline fromJson(JSONObject obj) {
            return new RealKline(
                    obj.getLongValue("timestamp"),
                    obj.getString("amount"),
                    obj.getString("open"),
                    obj.getString("high"),
                    obj.getString("low"),
                    obj.getString("close"),
                    obj.getString("volume"));
        }
    }
}