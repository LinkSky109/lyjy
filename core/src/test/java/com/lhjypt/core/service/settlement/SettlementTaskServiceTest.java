package com.lhjypt.core.service.settlement;

import com.lhjypt.core.config.DistributedLock;
import com.lhjypt.core.mapper.SettlementRuleMapper;
import com.lhjypt.core.mapper.SettlementTaskMapper;
import com.lhjypt.core.mapper.TradeRecordMapper;
import com.lhjypt.core.model.settlement.SettlementRule;
import com.lhjypt.core.model.settlement.SettlementTask;
import com.lhjypt.core.model.trade.TradeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettlementTaskServiceTest {

    private SettlementRuleMapper ruleMapper;
    private SettlementTaskMapper taskMapper;
    private TradeRecordMapper tradeRecordMapper;
    private SettlementTaskService service;

    @BeforeEach
    void setUp() {
        ruleMapper = mock(SettlementRuleMapper.class);
        taskMapper = mock(SettlementTaskMapper.class);
        tradeRecordMapper = mock(TradeRecordMapper.class);
        service = new SettlementTaskService(ruleMapper, taskMapper, tradeRecordMapper,
                mock(DistributedLock.class));
    }

    @Test
    @DisplayName("境内期货成交 → T+0 CNY 交收任务")
    void generateAppliesCnfutRule() {
        when(taskMapper.selectByTradeId("T1")).thenReturn(null);
        when(ruleMapper.selectByMarket("CN")).thenReturn(List.of(cnRule(0, "CNY", null)));
        when(ruleMapper.selectByMarketAndExchange("CN", "SHFE")).thenReturn(null);

        SettlementTask task = service.generateForTrade(trade("T1", "SHFE", "2026-10-06", "100"));

        assertThat(task.getStatus()).isEqualTo(SettlementTask.PENDING);
        assertThat(task.getCurrency()).isEqualTo("CNY");
        assertThat(task.getCycleDays()).isEqualTo(0);
        assertThat(task.getExpectedDate()).isEqualTo(LocalDate.of(2026, 10, 6));
        assertThat(task.getAmount()).isEqualByComparingTo("100");
        verify(taskMapper).insert(any(SettlementTask.class));
    }

    @Test
    @DisplayName("港股成交 → T+2 HKD 交收任务（截至日 +2）")
    void generateAppliesHkRule() {
        when(taskMapper.selectByTradeId("T-HK")).thenReturn(null);
        when(ruleMapper.selectByMarket("HK")).thenReturn(List.of(rule("R-HK", "HK", 2, "HKD", null)));
        when(ruleMapper.selectByMarketAndExchange("HK", "HKEX")).thenReturn(null);

        SettlementTask task = service.generateForTrade(trade("T-HK", "HKEX", "2026-10-06", "500"));

        assertThat(task.getCurrency()).isEqualTo("HKD");
        assertThat(task.getExpectedDate()).isEqualTo(LocalDate.of(2026, 10, 8));
    }

    @Test
    @DisplayName("重复生成为同一成交返回既有任务")
    void generateIsIdempotent() {
        SettlementTask existing = new SettlementTask();
        existing.setTaskId("STL-T-1");
        existing.setTradeId("T1");
        when(taskMapper.selectByTradeId("T1")).thenReturn(existing);

        SettlementTask task = service.generateForTrade(trade("T1", "SHFE", "2026-10-06", "100"));

        assertThat(task).isSameAs(existing);
        verify(taskMapper, never()).insert(any());
    }

    @Test
    @DisplayName("到期任务 → SETTLED（system 留痕）")
    void processDueSettles() {
        SettlementTask pending = new SettlementTask();
        pending.setTaskId("STL-T-1");
        pending.setTradeId("T1");
        pending.setStatus(SettlementTask.PENDING);
        pending.setExpectedDate(LocalDate.of(2026, 10, 5));
        when(taskMapper.selectPendingDue(LocalDate.of(2026, 10, 6)))
                .thenReturn(List.of(pending));

        int settled = service.processDue(LocalDate.of(2026, 10, 6));

        assertThat(settled).isEqualTo(1);
        assertThat(pending.getStatus()).isEqualTo(SettlementTask.SETTLED);
        assertThat(pending.getSettledBy()).isEqualTo("system");
        verify(taskMapper).updateById(pending);
    }

    @Test
    @DisplayName("手动交收与失败状态流转")
    void settleAndFail() {
        SettlementTask task = new SettlementTask();
        task.setTaskId("STL-T-2");
        task.setTradeId("T2");
        task.setStatus(SettlementTask.PENDING);
        when(taskMapper.selectByTaskId("STL-T-2")).thenReturn(task);

        SettlementTask settled = service.settle("STL-T-2", "fund-1");
        assertThat(settled.getStatus()).isEqualTo(SettlementTask.SETTLED);
        assertThat(settled.getSettledBy()).isEqualTo("fund-1");

        task.setStatus(SettlementTask.PENDING);
        SettlementTask failed = service.fail("STL-T-2", "账户余额不足");
        assertThat(failed.getStatus()).isEqualTo(SettlementTask.FAILED);
        assertThat(failed.getRemark()).contains("账户余额不足");
    }

    private static TradeRecord trade(String tradeId, String exchange, String day, String turnover) {
        TradeRecord trade = new TradeRecord();
        trade.setTradeId(tradeId);
        trade.setExchange(exchange);
        trade.setSymbol("rb2410");
        trade.setAccountId("ACC-1");
        trade.setTurnover(new BigDecimal(turnover));
        trade.setTradingDay(day);
        return trade;
    }

    private static SettlementRule cnRule(int cycle, String currency, String exchangeMatch) {
        return rule("R-CN-FUT", "CN", cycle, currency, exchangeMatch);
    }

    private static SettlementRule rule(String id, String market, int cycle, String currency,
                                       String exchangeMatch) {
        SettlementRule rule = new SettlementRule();
        rule.setRuleId(id);
        rule.setMarket(market);
        rule.setExchangeMatch(exchangeMatch);
        rule.setCycleDays(cycle);
        rule.setCurrency(currency);
        rule.setEnabled(true);
        return rule;
    }
}