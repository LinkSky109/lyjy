package com.lhjypt.core.service.market.source;

import java.util.List;

/**
 * 行情数据源 SPI（P3-006 多源与故障切换；P4-001 分页回补）。
 * 约定：入参 {@code pair} 为标准格式（如 {@code BTC_USDT}），实现内部转换为各自格式。
 */
public interface MarketDataSource {

    /** 源名称（配置中用，如 gateio / binance-vision）。 */
    String name();

    /** 拉取 K 线（失败抛异常，由上层重试/切换）。 */
    List<MarketCandle> fetch(String pair, String interval, int limit) throws Exception;

    /**
     * 分页拉取：{@code endTimeSec} 为结束时间游标（秒，含），为空表示取最近 {@code limit} 根。
     *
     * <p>默认实现忽略游标并退化为 {@link #fetch(String, String, int)}，供不支持分页的源沿用；
     * 支持历史回补的源应覆写本方法并让 {@link #supportsBackfill()} 返回 {@code true}。
     */
    default List<MarketCandle> fetch(String pair, String interval, int limit, Long endTimeSec)
            throws Exception {
        return fetch(pair, interval, limit);
    }

    /** 是否支持按结束时间游标回补更长历史（P4-001）。 */
    default boolean supportsBackfill() {
        return false;
    }
}
