package com.lhjypt.core.service.portfolio;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lhjypt.core.mapper.PortfolioMapper;
import com.lhjypt.core.mapper.PortfolioNavMapper;
import com.lhjypt.core.mapper.TradeRecordMapper;
import com.lhjypt.core.model.FundAccount;
import com.lhjypt.core.model.portfolio.Portfolio;
import com.lhjypt.core.model.portfolio.PortfolioNav;
import com.lhjypt.core.model.trade.TradeRecord;
import com.lhjypt.core.service.trade.TradeQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组合净值快照与策略贡献（P5-001 / P1-c）。
 * 净值当前以账户总权益为口径；引入份额后可归一化。
 */
@Service
public class PortfolioAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioAnalyticsService.class);

    private final PortfolioMapper portfolioMapper;
    private final PortfolioNavMapper navMapper;
    private final TradeRecordMapper tradeRecordMapper;
    private final TradeQueryService tradeQueryService;

    public PortfolioAnalyticsService(PortfolioMapper portfolioMapper,
                                     PortfolioNavMapper navMapper,
                                     TradeRecordMapper tradeRecordMapper,
                                     TradeQueryService tradeQueryService) {
        this.portfolioMapper = portfolioMapper;
        this.navMapper = navMapper;
        this.tradeRecordMapper = tradeRecordMapper;
        this.tradeQueryService = tradeQueryService;
    }

    public List<PortfolioNav> nav(String portfolioId, LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        requirePortfolio(portfolioId);
        return navMapper.selectRange(portfolioId, start, end);
    }

    @Transactional
    public PortfolioNav snapshot(String portfolioId) {
        Portfolio portfolio = requirePortfolio(portfolioId);
        String accountId = accountIdOf(portfolio);
        FundAccount account = tradeQueryService.account(accountId);
        BigDecimal equity = nullSafe(account.getTotalAsset());
        PortfolioNav existing = navMapper.selectByDate(portfolioId, LocalDate.now());
        PortfolioNav point = existing == null ? new PortfolioNav() : existing;
        point.setPortfolioId(portfolioId);
        point.setNavDate(LocalDate.now());
        point.setNav(equity);
        point.setEquity(equity);
        point.setCash(nullSafe(account.getAvailableCash()).add(nullSafe(account.getFrozenCash())));
        point.setSource("SNAPSHOT");
        if (existing == null) {
            point.setCreateTime(LocalDateTime.now());
            navMapper.insert(point);
        } else {
            navMapper.updateById(point);
        }
        return point;
    }

    public void snapshotQuietly(String portfolioId) {
        try {
            snapshot(portfolioId);
        } catch (RuntimeException error) {
            log.warn("组合净值快照失败: portfolio={} reason={}", portfolioId, error.getMessage());
        }
    }

    public Map<String, Object> attribution(String portfolioId, LocalDateTime from, LocalDateTime to) {
        Portfolio portfolio = requirePortfolio(portfolioId);
        String accountId = accountIdOf(portfolio);
        LocalDateTime end = to == null ? LocalDateTime.now() : to;
        LocalDateTime start = from == null ? end.minusDays(30) : from;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }

        List<TradeRecord> trades = tradeRecordMapper.selectByAccountAndTimeRange(accountId, start, end);
        Map<String, Contribution> grouped = new LinkedHashMap<>();
        for (TradeRecord trade : trades) {
            String strategyId = trade.getStrategyId() == null || trade.getStrategyId().isBlank()
                    ? "UNASSIGNED" : trade.getStrategyId();
            Contribution item = grouped.computeIfAbsent(strategyId, Contribution::new);
            item.trades++;
            item.turnover = item.turnover.add(nullSafe(trade.getTurnover()));
            item.realizedPnl = item.realizedPnl.add(nullSafe(trade.getRealizedPnl()));
        }
        BigDecimal totalPnl = grouped.values().stream()
                .map(item -> item.realizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Contribution item : grouped.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("strategyId", item.strategyId);
            row.put("trades", item.trades);
            row.put("turnover", item.turnover);
            row.put("realizedPnl", item.realizedPnl);
            row.put("contribution", percentOf(item.realizedPnl, totalPnl));
            items.add(row);
        }
        items.sort((a, b) -> ((BigDecimal) b.get("realizedPnl"))
                .compareTo((BigDecimal) a.get("realizedPnl")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("portfolioId", portfolioId);
        result.put("accountId", accountId);
        result.put("from", start);
        result.put("to", end);
        result.put("totalPnl", totalPnl);
        result.put("items", items);
        return result;
    }

    private Portfolio requirePortfolio(String portfolioId) {
        Portfolio portfolio = portfolioMapper.selectByPortfolioId(portfolioId);
        if (portfolio == null) {
            throw new IllegalArgumentException("组合不存在: " + portfolioId);
        }
        return portfolio;
    }

    private static String accountIdOf(Portfolio portfolio) {
        try {
            JSONObject config = JSON.parseObject(
                    portfolio.getConfigJson() == null || portfolio.getConfigJson().isBlank()
                            ? "{}" : portfolio.getConfigJson());
            String accountId = config.getString("accountId");
            return accountId == null || accountId.isBlank() ? "DEFAULT-001" : accountId;
        } catch (RuntimeException error) {
            return "DEFAULT-001";
        }
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal percentOf(BigDecimal value, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return value.multiply(BigDecimal.valueOf(100))
                .divide(total, 1, RoundingMode.HALF_UP);
    }

    private static class Contribution {
        private final String strategyId;
        private int trades;
        private BigDecimal turnover = BigDecimal.ZERO;
        private BigDecimal realizedPnl = BigDecimal.ZERO;

        private Contribution(String strategyId) {
            this.strategyId = strategyId;
        }
    }
}
