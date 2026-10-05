package com.lhjypt.core.service.var;

import java.math.BigDecimal;
import java.util.List;

/**
 * VaR 历史行情数据源（本地可运行 + 生产可替换）。
 */
public interface VarMarketDataSource {

    /**
     * 查询标的日线收盘价，按时间升序返回。
     *
     * @param symbol       标的
     * @param lookbackDays 期望返回的最近交易日数量
     */
    List<BigDecimal> closingPrices(String symbol, int lookbackDays);
}
