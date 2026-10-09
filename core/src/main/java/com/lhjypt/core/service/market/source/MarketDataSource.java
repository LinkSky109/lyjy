package com.lhjypt.core.service.market.source;

import java.util.List;

/**
 * 行情数据源 SPI（P3-006 多源与故障切换）。
 * 约定：入参 {@code pair} 为标准格式（如 {@code BTC_USDT}），实现内部转换为各自格式。
 */
public interface MarketDataSource {

    /** 源名称（配置中用，如 gateio / binance-vision）。 */
    String name();

    /** 拉取 K 线（失败抛异常，由上层重试/切换）。 */
    List<MarketCandle> fetch(String pair, String interval, int limit) throws Exception;
}