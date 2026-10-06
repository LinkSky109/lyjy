package com.lhjypt.core.service.regulatory;

import com.lhjypt.core.mapper.AmlRuleMapper;
import com.lhjypt.core.mapper.RegulatoryReportBatchMapper;
import com.lhjypt.core.mapper.RegulatoryReportItemMapper;
import com.lhjypt.core.mapper.TradeRecordMapper;
import com.lhjypt.core.model.regulatory.RegulatoryReportBatch;
import com.lhjypt.core.model.regulatory.RegulatoryReportItem;
import com.lhjypt.core.model.trade.TradeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegulatoryReportServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 6);

    private RegulatoryReportBatchMapper batchMapper;
    private RegulatoryReportItemMapper itemMapper;
    private TradeRecordMapper tradeRecordMapper;
    private AmlRuleMapper amlRuleMapper;
    private RegulatoryReportService service;

    @BeforeEach
    void setUp() {
        batchMapper = mock(RegulatoryReportBatchMapper.class);
        itemMapper = mock(RegulatoryReportItemMapper.class);
        tradeRecordMapper = mock(TradeRecordMapper.class);
        amlRuleMapper = mock(AmlRuleMapper.class);
        service = new RegulatoryReportService(batchMapper, itemMapper, tradeRecordMapper, amlRuleMapper);
    }

    @Test
    @DisplayName("成交日报：生成 READY 批次并逐笔落明细、汇总金额")
    void dailyReportGeneratesBatchAndItems() {
        when(tradeRecordMapper.selectByTradingDay("2026-10-06"))
                .thenReturn(List.of(trade("T1", "100"), trade("T2", "200")));
        when(batchMapper.selectByTypeAndDate("DAILY_TRADE", DAY)).thenReturn(null);

        RegulatoryReportBatch batch = service.run("DAILY_TRADE", DAY, "comp-1");

        assertThat(batch.getStatus()).isEqualTo(RegulatoryReportBatch.READY);
        assertThat(batch.getReportType()).isEqualTo("DAILY_TRADE");
        assertThat(batch.getTotalItems()).isEqualTo(2);
        assertThat(batch.getTotalAmount()).isEqualByComparingTo("300");

        verify(batchMapper).insert(batch);
        ArgumentCaptor<RegulatoryReportItem> captor =
                ArgumentCaptor.forClass(RegulatoryReportItem.class);
        verify(itemMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues().get(0).getContentJson()).contains("\"tradeId\":\"T1\"");
    }

    @Test
    @DisplayName("重复运行：READY 批次幂等返回，不重复生成")
    void idempotentRunReturnsExisting() {
        RegulatoryReportBatch existing = batch("REG-1", "DAILY_TRADE", RegulatoryReportBatch.READY);
        when(batchMapper.selectByTypeAndDate("DAILY_TRADE", DAY)).thenReturn(existing);

        RegulatoryReportBatch result = service.run("DAILY_TRADE", DAY, "comp-1");

        assertThat(result).isSameAs(existing);
        verify(tradeRecordMapper, never()).selectByTradingDay(anyString());
    }

    @Test
    @DisplayName("无成交记录时报错")
    void noTradesThrows() {
        when(tradeRecordMapper.selectByTradingDay("2026-10-06")).thenReturn(List.of());
        assertThatThrownBy(() -> service.run("DAILY_TRADE", DAY, "u"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("无成交记录");
    }

    @Test
    @DisplayName("大额报备：仅纳入达到大额阈值的成交")
    void largeTradeFiltersByThreshold() {
        when(amlRuleMapper.selectEnabledByCode("LARGE_SINGLE")).thenReturn(null); // 走缺省阈值 50 万
        when(tradeRecordMapper.selectByTradingDay("2026-10-06"))
                .thenReturn(List.of(trade("T1", "100000"), trade("T2", "600000")));

        RegulatoryReportBatch batch = service.run("LARGE_TRADE", DAY, "comp-1");

        assertThat(batch.getTotalItems()).isEqualTo(1);
        assertThat(batch.getTotalAmount()).isEqualByComparingTo("600000");
    }

    @Test
    @DisplayName("报送：READY→SUBMITTED 并留痕；非 READY 拒绝")
    void submitTransitionsAndRejects() {
        RegulatoryReportBatch ready = batch("REG-1", "DAILY_TRADE", RegulatoryReportBatch.READY);
        when(batchMapper.selectByBatchId("REG-1")).thenReturn(ready);

        RegulatoryReportBatch submitted = service.submit("REG-1", "comp-1");

        assertThat(submitted.getStatus()).isEqualTo(RegulatoryReportBatch.SUBMITTED);
        assertThat(submitted.getSubmitBy()).isEqualTo("comp-1");
        assertThat(submitted.getSubmitTime()).isNotNull();
        verify(batchMapper).updateById(ready);

        RegulatoryReportBatch generating = batch("REG-2", "DAILY_TRADE", RegulatoryReportBatch.GENERATING);
        when(batchMapper.selectByBatchId("REG-2")).thenReturn(generating);
        assertThatThrownBy(() -> service.submit("REG-2", "comp-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("仅 READY");
    }

    private static TradeRecord trade(String tradeId, String turnover) {
        TradeRecord trade = new TradeRecord();
        trade.setTradeId(tradeId);
        trade.setOrderId("O-" + tradeId);
        trade.setAccountId("ACC-1");
        trade.setStrategyId("S-1");
        trade.setSymbol("rb2410");
        trade.setExchange("SHFE");
        trade.setDirection("BUY");
        trade.setOffsetFlag("OPEN");
        trade.setQuantity(new BigDecimal("1"));
        trade.setPrice(new BigDecimal("3500"));
        trade.setTurnover(new BigDecimal(turnover));
        trade.setCommission(new BigDecimal("1"));
        trade.setTradeTime(LocalDateTime.of(2026, 10, 6, 10, 0));
        trade.setTradingDay("2026-10-06");
        return trade;
    }

    private static RegulatoryReportBatch batch(String batchId, String type, String status) {
        RegulatoryReportBatch batch = new RegulatoryReportBatch();
        batch.setBatchId(batchId);
        batch.setReportType(type);
        batch.setReportDate(DAY);
        batch.setStatus(status);
        return batch;
    }
}