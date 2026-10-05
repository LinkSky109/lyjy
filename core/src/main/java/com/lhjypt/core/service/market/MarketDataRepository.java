package com.lhjypt.core.service.market;

import com.lhjypt.core.model.market.BarData;
import com.lhjypt.core.model.market.TickData;
import java.util.List;

/**
 * 行情存储接口 - ClickHouse时序数据存储
 */
public interface MarketDataRepository {

    /**
     * 保存tick数据
     */
    void saveTick(TickData tick);

    /**
     * 批量保存tick
     */
    void saveTicks(List<TickData> ticks);

    /**
     * 保存K线
     */
    void saveBar(BarData bar);

    /**
     * 查询历史tick
     */
    List<TickData> queryTicks(String symbol, String startTime, String endTime, int limit);

    /**
     * 查询历史K线
     */
    List<BarData> queryBars(String symbol, String frequency, String startTime, String endTime, int limit);

    /**
     * 获取最新tick
     */
    TickData getLatestTick(String symbol);
}
