package com.lhjypt.core.service.market.impl;

import com.alibaba.fastjson2.JSON;
import com.lhjypt.core.model.market.BarData;
import com.lhjypt.core.model.market.TickData;
import com.lhjypt.core.service.market.MarketDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ClickHouse行情存储实现
 * 负责tick/bar时序数据的持久化与查询
 */
@Repository
@RequiredArgsConstructor
public class ClickHouseMarketDataRepository implements MarketDataRepository {
    private static final Logger log = LoggerFactory.getLogger(ClickHouseMarketDataRepository.class);


    @Qualifier("clickHouseJdbcTemplate")
    private final JdbcTemplate clickHouseJdbcTemplate;

    private static final String INSERT_TICK_SQL = """
            INSERT INTO lhjypt.tick_data
            (symbol, last_price, pre_close, open, high, low, volume, turnover, open_interest,
             bid_price1, bid_price2, bid_price3, bid_price4, bid_price5,
             bid_volume1, bid_volume2, bid_volume3, bid_volume4, bid_volume5,
             ask_price1, ask_price2, ask_price3, ask_price4, ask_price5,
             ask_volume1, ask_volume2, ask_volume3, ask_volume4, ask_volume5,
             tick_time, recv_time)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    private static final String INSERT_BAR_SQL = """
            INSERT INTO lhjypt.bar_data
            (symbol, frequency, open, high, low, close, volume, turnover, open_interest, bar_time)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """;

    @Override
    public void saveTick(TickData tick) {
        try {
            clickHouseJdbcTemplate.update(INSERT_TICK_SQL,
                    tick.getSymbol(), tick.getLastPrice(), tick.getPreClose(),
                    tick.getOpen(), tick.getHigh(), tick.getLow(),
                    tick.getVolume(), tick.getTurnover(), tick.getOpenInterest(),
                    tick.getBidPrice1(), tick.getBidPrice2(), tick.getBidPrice3(),
                    tick.getBidPrice4(), tick.getBidPrice5(),
                    tick.getBidVolume1(), tick.getBidVolume2(), tick.getBidVolume3(),
                    tick.getBidVolume4(), tick.getBidVolume5(),
                    tick.getAskPrice1(), tick.getAskPrice2(), tick.getAskPrice3(),
                    tick.getAskPrice4(), tick.getAskPrice5(),
                    tick.getAskVolume1(), tick.getAskVolume2(), tick.getAskVolume3(),
                    tick.getAskVolume4(), tick.getAskVolume5(),
                    tick.getTickTime(), tick.getRecvTime());
        } catch (Exception e) {
            log.warn("ClickHouse写入tick失败（可能服务未启动）: {} - {}", tick.getSymbol(), e.getMessage());
        }
    }

    @Override
    public void saveTicks(List<TickData> ticks) {
        for (TickData tick : ticks) {
            saveTick(tick);
        }
    }

    @Override
    public void saveBar(BarData bar) {
        try {
            clickHouseJdbcTemplate.update(INSERT_BAR_SQL,
                    bar.getSymbol(), bar.getFrequency(), bar.getOpen(),
                    bar.getHigh(), bar.getLow(), bar.getClose(),
                    bar.getVolume(), bar.getTurnover(), bar.getOpenInterest(),
                    bar.getBarTime());
        } catch (Exception e) {
            log.warn("ClickHouse写入bar失败: {} - {}", bar.getSymbol(), e.getMessage());
        }
    }

    @Override
    public List<TickData> queryTicks(String symbol, String startTime, String endTime, int limit) {
        String sql = """
                SELECT * FROM lhjypt.tick_data
                WHERE symbol = ? AND tick_time >= ? AND tick_time <= ?
                ORDER BY tick_time DESC LIMIT ?
                """;
        try {
            return clickHouseJdbcTemplate.query(sql, this::mapTick, symbol, startTime, endTime, limit);
        } catch (Exception e) {
            log.warn("查询tick失败: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<BarData> queryBars(String symbol, String frequency, String startTime, String endTime, int limit) {
        String sql = """
                SELECT * FROM lhjypt.bar_data
                WHERE symbol = ? AND frequency = ? AND bar_time >= ? AND bar_time <= ?
                ORDER BY bar_time DESC LIMIT ?
                """;
        try {
            return clickHouseJdbcTemplate.query(sql, this::mapBar, symbol, frequency, startTime, endTime, limit);
        } catch (Exception e) {
            log.warn("查询bar失败: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public TickData getLatestTick(String symbol) {
        String sql = "SELECT * FROM lhjypt.tick_data WHERE symbol = ? ORDER BY tick_time DESC LIMIT 1";
        try {
            List<TickData> ticks = clickHouseJdbcTemplate.query(sql, this::mapTick, symbol);
            return ticks.isEmpty() ? null : ticks.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private TickData mapTick(ResultSet rs, int rowNum) throws SQLException {
        TickData tick = new TickData();
        tick.setSymbol(rs.getString("symbol"));
        tick.setLastPrice(rs.getBigDecimal("last_price"));
        tick.setPreClose(rs.getBigDecimal("pre_close"));
        tick.setOpen(rs.getBigDecimal("open"));
        tick.setHigh(rs.getBigDecimal("high"));
        tick.setLow(rs.getBigDecimal("low"));
        tick.setVolume(rs.getLong("volume"));
        tick.setTurnover(rs.getBigDecimal("turnover"));
        tick.setOpenInterest(rs.getLong("open_interest"));
        tick.setTickTime(rs.getObject("tick_time", LocalDateTime.class));
        tick.setRecvTime(rs.getObject("recv_time", LocalDateTime.class));
        return tick;
    }

    private BarData mapBar(ResultSet rs, int rowNum) throws SQLException {
        BarData bar = new BarData();
        bar.setSymbol(rs.getString("symbol"));
        bar.setFrequency(rs.getString("frequency"));
        bar.setOpen(rs.getBigDecimal("open"));
        bar.setHigh(rs.getBigDecimal("high"));
        bar.setLow(rs.getBigDecimal("low"));
        bar.setClose(rs.getBigDecimal("close"));
        bar.setVolume(rs.getLong("volume"));
        bar.setTurnover(rs.getBigDecimal("turnover"));
        bar.setOpenInterest(rs.getLong("open_interest"));
        bar.setBarTime(rs.getObject("bar_time", LocalDateTime.class));
        return bar;
    }
}
