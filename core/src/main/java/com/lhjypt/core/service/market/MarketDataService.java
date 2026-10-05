package com.lhjypt.core.service.market;

import com.lhjypt.core.model.market.BarData;
import com.lhjypt.core.model.market.TickData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 行情服务 - 统一行情接入、存储、查询入口
 */
@Service
@RequiredArgsConstructor
public class MarketDataService {
    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);


    private final MarketDataProvider provider;
    private final MarketDataRepository repository;

    /**
     * 启动行情服务
     */
    public void start() {
        provider.connect();
        log.info("行情服务已启动，行情源: {}", provider.getName());
    }

    /**
     * 停止行情服务
     */
    public void stop() {
        provider.disconnect();
        log.info("行情服务已停止");
    }

    /**
     * 订阅标的
     */
    public void subscribe(String symbol) {
        provider.subscribe(symbol);
    }

    /**
     * 批量订阅
     */
    public void subscribeAll(List<String> symbols) {
        symbols.forEach(this::subscribe);
    }

    /**
     * 取消订阅
     */
    public void unsubscribe(String symbol) {
        provider.unsubscribe(symbol);
    }

    /**
     * 获取已订阅标的
     */
    public Set<String> getSubscribedSymbols() {
        return provider.getSubscribedSymbols();
    }

    /**
     * 获取最新tick（内存缓存，低延迟）
     */
    public TickData getLatestTick(String symbol) {
        return provider.getLatestTick(symbol);
    }

    /**
     * 查询历史tick
     */
    public List<TickData> queryTicks(String symbol, String startTime, String endTime, int limit) {
        return repository.queryTicks(symbol, startTime, endTime, limit);
    }

    /**
     * 查询历史K线
     */
    public List<BarData> queryBars(String symbol, String frequency, String startTime, String endTime, int limit) {
        return repository.queryBars(symbol, frequency, startTime, endTime, limit);
    }

    /**
     * 行情源状态
     */
    public boolean isRunning() {
        return provider.isConnected();
    }
}
