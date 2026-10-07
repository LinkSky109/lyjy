package com.lhjypt.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 真实行情源配置（OBS-005 Gate.io 现货 K 线）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lhjypt.market.real")
public class RealMarketProperties {

    /** 是否启用真实行情 REST（默认启用，手动 refresh 才拉取） */
    private boolean enabled = true;

    /** Gate.io 现货 K 线 API 地址 */
    private String baseUrl = "https://api.gateio.ws/api/v4/spot/candlesticks";

    /** 默认交易对（Gate 格式 BTC_USDT） */
    private String currencyPair = "BTC_USDT";

    /** 默认 K 线周期：1m/5m/15m/1h/1d */
    private String interval = "1h";

    /** 默认拉取条数 */
    private int limit = 200;

    /** HTTP 超时（秒） */
    private int timeoutSeconds = 10;

    /** 定时自动刷新间隔（ms，>=5000 才启用；通过 enabled 关闭） */
    private long refreshIntervalMs = 900000;
}