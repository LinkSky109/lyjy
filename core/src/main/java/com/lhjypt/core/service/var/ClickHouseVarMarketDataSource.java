package com.lhjypt.core.service.var;

import com.lhjypt.core.model.market.BarData;
import com.lhjypt.core.service.market.MarketDataService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 ClickHouse {@code lhjypt.bar_data} 日线的 VaR 行情数据源。
 * ClickHouse 不可用时返回空列表，由上层判定 INSUFFICIENT_DATA（不产出虚假数值）。
 */
@Component
@RequiredArgsConstructor
public class ClickHouseVarMarketDataSource implements VarMarketDataSource {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseVarMarketDataSource.class);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MarketDataService marketDataService;

    @Override
    public List<BigDecimal> closingPrices(String symbol, int lookbackDays) {
        if (symbol == null || symbol.isBlank()) {
            return List.of();
        }
        int limit = Math.max(lookbackDays, 1);
        // 日历日放宽 2 倍 + 缓冲，覆盖周末与非交易日
        String start = LocalDate.now().minusDays(limit * 2L + 15).atStartOfDay()
                .format(TIME_FORMAT);
        String end = LocalDate.now().plusDays(1).atTime(23, 59, 59).format(TIME_FORMAT);
        try {
            List<BarData> bars = marketDataService.queryBars(symbol, "1d", start, end, limit);
            if (bars == null || bars.isEmpty()) {
                return List.of();
            }
            List<BigDecimal> closes = new ArrayList<>(bars.size());
            // queryBars 按 bar_time DESC 返回，反转为升序
            for (int i = bars.size() - 1; i >= 0; i--) {
                BigDecimal close = bars.get(i).getClose();
                if (close != null) {
                    closes.add(close);
                }
            }
            return closes;
        } catch (Exception error) {
            log.warn("查询 VaR 日线失败 symbol={} err={}", symbol, error.getMessage());
            return List.of();
        }
    }
}
