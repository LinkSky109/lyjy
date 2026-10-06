package com.lhjypt.core.service.settlement;

import com.lhjypt.core.config.DistributedLock;
import com.lhjypt.core.mapper.SettlementRuleMapper;
import com.lhjypt.core.mapper.SettlementTaskMapper;
import com.lhjypt.core.mapper.TradeRecordMapper;
import com.lhjypt.core.model.settlement.SettlementRule;
import com.lhjypt.core.model.settlement.SettlementTask;
import com.lhjypt.core.model.trade.TradeRecord;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 跨境交收状态机（P2 模块一 §1.4）。
 *
 * <p>按交易 + 交收规则生成交收任务（T+N、币种），状态
 * {@code PENDING → SETTLING → SETTLED / FAILED}；每日定时：为当日成交生成任务并对到期任务完成交收。
 */
@Service
@RequiredArgsConstructor
public class SettlementTaskService {

    private static final Logger log = LoggerFactory.getLogger(SettlementTaskService.class);

    private final SettlementRuleMapper ruleMapper;
    private final SettlementTaskMapper taskMapper;
    private final TradeRecordMapper tradeRecordMapper;
    private final DistributedLock distributedLock;

    /** 每日：为当日成交生成交收任务 + 对到期任务完成交收。 */
    @Scheduled(cron = "${lhjypt.settlement.daily-cron:0 5 0 * * *}")
    public void scheduledDaily() {
        distributedLock.execute("settlement.daily", () -> {
            LocalDate today = LocalDate.now();
            generateForDay(today);
            processDue(today);
        });
    }

    public List<SettlementRule> rules() {
        return ruleMapper.selectAll();
    }

    @Transactional
    public int generateForDay(LocalDate tradeDate) {
        LocalDate date = tradeDate == null ? LocalDate.now() : tradeDate;
        int generated = 0;
        int skipped = 0;
        List<TradeRecord> trades = tradeRecordMapper.selectByTradingDay(date.toString());
        if (trades.isEmpty()) {
            // 兼容 BASIC ISO 格式的 trading_day（如 20261005）
            trades = tradeRecordMapper.selectByTradingDay(
                    DateTimeFormatter.BASIC_ISO_DATE.format(date));
        }
        for (TradeRecord trade : trades) {
            if (generateForTrade(trade) != null) {
                generated++;
            } else {
                skipped++;
            }
        }
        log.info("生成交收任务: tradeDate={} generated={} skipped={}", date, generated, skipped);
        return generated;
    }

    @Transactional
    public SettlementTask generateForTrade(TradeRecord trade) {
        SettlementTask existing = taskMapper.selectByTradeId(trade.getTradeId());
        if (existing != null) {
            log.warn("该成交已存在交收任务: tradeId={}", trade.getTradeId());
            return existing;
        }
        SettlementRule rule = resolveRule(trade.getExchange());
        SettlementTask task = new SettlementTask();
        task.setTaskId("STL-T-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        task.setTradeId(trade.getTradeId());
        task.setAccountId(trade.getAccountId());
        task.setSymbol(trade.getSymbol());
        task.setExchange(trade.getExchange());
        task.setCurrency(rule == null ? defaultCurrency(trade.getExchange()) : rule.getCurrency());
        task.setCycleDays(rule == null ? 0 : rule.getCycleDays());
        task.setTradeDate(parseTradeDay(trade.getTradingDay()));
        task.setExpectedDate(task.getTradeDate().plusDays(task.getCycleDays()));
        task.setAmount(trade.getTurnover() == null ? BigDecimal.ZERO : trade.getTurnover());
        task.setStatus(SettlementTask.PENDING);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.insert(task);
        return task;
    }

    /** 对到期（expected ≤ asOf）的 PENDING 任务完成交收。 */
    @Transactional
    public int processDue(LocalDate asOf) {
        LocalDate date = asOf == null ? LocalDate.now() : asOf;
        int settled = 0;
        for (SettlementTask task : taskMapper.selectPendingDue(date)) {
            task.setStatus(SettlementTask.SETTLED);
            task.setSettledBy("system");
            task.setSettledAt(LocalDateTime.now());
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
            settled++;
        }
        log.info("交收到期处理完成: asOf={} settled={}", date, settled);
        return settled;
    }

    @Transactional
    public SettlementTask settle(String taskId, String operator) {
        SettlementTask task = require(taskId);
        if (SettlementTask.SETTLED.equals(task.getStatus())) {
            return task;
        }
        task.setStatus(SettlementTask.SETTLED);
        task.setSettledBy(operator);
        task.setSettledAt(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        return task;
    }

    @Transactional
    public SettlementTask fail(String taskId, String reason) {
        SettlementTask task = require(taskId);
        task.setStatus(SettlementTask.FAILED);
        task.setRemark("交收失败: " + (reason == null ? "" : reason));
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        return task;
    }

    public List<SettlementTask> list(String status) {
        return taskMapper.selectByStatus(status == null || status.isBlank() ? null : status.trim().toUpperCase());
    }

    public SettlementTask detail(String taskId) {
        return require(taskId);
    }

    /** 按交易所推断市场并选择规则（先具体交易所匹配，再回落市场默认）。 */
    private SettlementRule resolveRule(String exchange) {
        if (exchange == null || exchange.isBlank()) {
            return ruleMapper.selectByMarket("CN").stream()
                    .filter(rule -> rule.getExchangeMatch() == null).findFirst().orElse(null);
        }
        String market = inferMarket(exchange);
        SettlementRule exact = ruleMapper.selectByMarketAndExchange(market, exchange);
        if (exact != null) {
            return exact;
        }
        return ruleMapper.selectByMarket(market).stream()
                .filter(rule -> rule.getExchangeMatch() == null).findFirst().orElse(null);
    }

    private static String inferMarket(String exchange) {
        String value = exchange.toUpperCase(Locale.ROOT);
        if (value.contains("HK")) {
            return "HK";
        }
        if (value.contains("NASDAQ") || value.contains("NYSE")) {
            return "US";
        }
        return "CN";
    }

    private static String defaultCurrency(String exchange) {
        return switch (inferMarket(exchange)) {
            case "HK" -> "HKD";
            case "US" -> "USD";
            default -> "CNY";
        };
    }

    private static LocalDate parseTradeDay(String tradingDay) {
        if (tradingDay == null || tradingDay.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(tradingDay, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception ignored) {
            return LocalDate.parse(tradingDay, DateTimeFormatter.BASIC_ISO_DATE);
        }
    }

    private SettlementTask require(String taskId) {
        SettlementTask task = taskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new IllegalArgumentException("交收任务不存在: " + taskId);
        }
        return task;
    }
}