package com.lhjypt.core.service.market;

import com.lhjypt.core.model.market.TickData;
import java.util.Set;

/**
 * 行情提供者接口 - 抽象不同行情源（CTP/模拟/回放）
 */
public interface MarketDataProvider {

    /**
     * 连接行情源
     */
    void connect();

    /**
     * 断开行情源
     */
    void disconnect();

    /**
     * 订阅标的行情
     */
    void subscribe(String symbol);

    /**
     * 取消订阅
     */
    void unsubscribe(String symbol);

    /**
     * 获取已订阅标的列表
     */
    Set<String> getSubscribedSymbols();

    /**
     * 获取最新tick
     */
    TickData getLatestTick(String symbol);

    /**
     * 行情源名称
     */
    String getName();

    /**
     * 是否连接
     */
    boolean isConnected();
}
