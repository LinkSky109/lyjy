package com.lhjypt.core.service.settlement;

import com.lhjypt.core.mapper.FundAccountMapper;
import com.lhjypt.core.mapper.FundFlowMapper;
import com.lhjypt.core.mapper.PositionMapper;
import com.lhjypt.core.mapper.SettlementBatchMapper;
import com.lhjypt.core.mapper.SettlementDiffItemMapper;
import com.lhjypt.core.mapper.TradeRecordMapper;
import com.lhjypt.core.model.FundAccount;
import com.lhjypt.core.model.Position;
import com.lhjypt.core.model.settlement.SettlementBatch;
import com.lhjypt.core.model.settlement.SettlementDiffItem;
import com.lhjypt.core.model.trade.AccountInfo;
import com.lhjypt.core.model.fund.FundFlow;
import com.lhjypt.core.model.trade.PositionDetail;
import com.lhjypt.core.model.trade.TradeRecord;
import com.lhjypt.core.model.trade.TradeReport;
import com.lhjypt.core.service.trade.gateway.GatewayRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailySettlementServiceTest {

    private SettlementBatchMapper batchMapper;
    private SettlementDiffItemMapper diffMapper;
    private TradeRecordMapper tradeMapper;
    private FundAccountMapper accountMapper;
    private FundFlowMapper fundFlowMapper;
    private PositionMapper positionMapper;
    private GatewayRouter gatewayRouter;
    private DailySettlementService service;
    private List<SettlementDiffItem> insertedDiffs;

    @BeforeEach
    void setUp() {
        batchMapper = mock(SettlementBatchMapper.class);
        diffMapper = mock(SettlementDiffItemMapper.class);
        tradeMapper = mock(TradeRecordMapper.class);
        accountMapper = mock(FundAccountMapper.class);
        fundFlowMapper = mock(FundFlowMapper.class);
        positionMapper = mock(PositionMapper.class);
        gatewayRouter = mock(GatewayRouter.class);
        service = new DailySettlementService(
                batchMapper, diffMapper, tradeMapper,
                accountMapper, fundFlowMapper, positionMapper, gatewayRouter);
        ReflectionTestUtils.setField(service, "tolerance", new BigDecimal("0.01"));
        insertedDiffs = new ArrayList<>();
        when(diffMapper.insert(any(SettlementDiffItem.class)))
                .thenAnswer(invocation -> {
                    insertedDiffs.add(invocation.getArgument(0));
                    return 1;
                });
        when(gatewayRouter.isAnyAuthenticated()).thenReturn(true);
        when(fundFlowMapper.selectByTimeRange(any(), any())).thenReturn(List.of());
    }

    @Test
    void runsCleanSettlementWithNoDifferences() {
        LocalDate date = LocalDate.of(2026, 10, 5);
        when(batchMapper.selectByTradeDate(date)).thenReturn(null);
        when(tradeMapper.selectByTradeTimeRange(
                date.atStartOfDay(), date.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(trade("A")));
        FundAccount account = account("A", "1000", "500", "100");
        when(accountMapper.selectList(null)).thenReturn(List.of(account));
        when(gatewayRouter.queryAccount(null)).thenReturn(counter("1000", "500", "100"));
        when(positionMapper.selectByAccount("A")).thenReturn(List.of(position("A", "rb", "2")));
        when(gatewayRouter.queryPositionsAll()).thenReturn(List.of(gatewayPosition("rb", "2")));
        TradeReport report = tradeReport(date);
        when(gatewayRouter.queryTradesAll()).thenReturn(List.of(report));

        SettlementBatch batch = service.run(date);

        assertEquals(SettlementBatch.REVIEWING, batch.getStatus());
        assertEquals(0, batch.getTotalDiff());
        assertEquals(0, batch.getTotalFee().compareTo(new BigDecimal("2.1")));
        assertEquals(0, batch.getTotalPnl().compareTo(new BigDecimal("10")));
        assertEquals(0, insertedDiffs.size());
    }

    @Test
    void createsOpenDiffWhenFundDoesNotMatch() {
        LocalDate date = LocalDate.of(2026, 10, 5);
        when(batchMapper.selectByTradeDate(date)).thenReturn(null);
        when(tradeMapper.selectByTradeTimeRange(any(), any())).thenReturn(List.of(trade("A")));
        FundAccount account = account("A", "1000", "500", "100");
        when(accountMapper.selectList(null)).thenReturn(List.of(account));
        when(gatewayRouter.queryAccount(null)).thenReturn(counter("999", "500", "100"));
        when(positionMapper.selectByAccount("A")).thenReturn(List.of());
        when(gatewayRouter.queryPositionsAll()).thenReturn(List.of());
        when(gatewayRouter.queryTradesAll()).thenReturn(List.of(tradeReport(date)));

        SettlementBatch batch = service.run(date);

        assertEquals(1, batch.getTotalDiff());
        assertEquals(SettlementDiffItem.OPEN, insertedDiffs.get(0).getStatus());
        assertEquals("fund.total", insertedDiffs.get(0).getFieldName());
    }

    @Test
    void sumsFeeAndPnlFromFundFlows() {
        LocalDate date = LocalDate.of(2026, 10, 5);
        when(batchMapper.selectByTradeDate(date)).thenReturn(null);
        TradeRecord zeroFeeTrade = trade("A");
        zeroFeeTrade.setCommission(BigDecimal.ZERO);
        when(tradeMapper.selectByTradeTimeRange(any(), any()))
                .thenReturn(List.of(zeroFeeTrade));
        when(fundFlowMapper.selectByTimeRange(any(), any()))
                .thenReturn(List.of(flow("COMMISSION", "-2.1"), flow("REALIZED_PNL", "10")));
        FundAccount account = account("A", "1000", "500", "100");
        when(accountMapper.selectList(null)).thenReturn(List.of(account));
        when(gatewayRouter.queryAccount(null)).thenReturn(counter("1000", "500", "100"));
        when(positionMapper.selectByAccount("A")).thenReturn(List.of());
        when(gatewayRouter.queryPositionsAll()).thenReturn(List.of());
        when(gatewayRouter.queryTradesAll()).thenReturn(List.of(tradeReport(date)));

        SettlementBatch batch = service.run(date);

        assertEquals(0, batch.getTotalFee().compareTo(new BigDecimal("2.1")));
        assertEquals(0, batch.getTotalPnl().compareTo(new BigDecimal("10")));
    }

    @Test
    void rejectsRerunWithoutForce() {
        LocalDate date = LocalDate.of(2026, 10, 5);
        SettlementBatch existing = batch(date, SettlementBatch.REVIEWING);
        when(batchMapper.selectByTradeDate(date)).thenReturn(existing);

        assertThrows(IllegalStateException.class, () -> service.run(date));
    }

    @Test
    void marksFailedWhenGatewayUnavailable() {
        LocalDate date = LocalDate.of(2026, 10, 5);
        when(batchMapper.selectByTradeDate(date)).thenReturn(null);
        when(gatewayRouter.isAnyAuthenticated()).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.run(date));

        ArgumentCaptor<SettlementBatch> captor =
                ArgumentCaptor.forClass(SettlementBatch.class);
        verify(batchMapper, org.mockito.Mockito.atLeastOnce()).updateById(captor.capture());
        assertEquals(SettlementBatch.FAILED,
                captor.getAllValues().get(captor.getAllValues().size() - 1).getStatus());
    }

    @Test
    void adjustsThenClosesBatch() {
        SettlementDiffItem diff = new SettlementDiffItem();
        diff.setDiffId("D1");
        diff.setBatchId("B1");
        diff.setStatus(SettlementDiffItem.OPEN);
        SettlementBatch batch = batch(LocalDate.now(), SettlementBatch.REVIEWING);
        batch.setBatchId("B1");
        when(diffMapper.selectByDiffId("D1")).thenReturn(diff);
        when(batchMapper.selectByBatchId("B1")).thenReturn(batch);
        when(diffMapper.selectByBatchId("B1")).thenReturn(List.of(diff));

        assertThrows(IllegalStateException.class, () -> service.closeBatch("B1"));
        SettlementDiffItem adjusted = service.adjustDiff("D1", "fund", "处理完成");
        assertEquals(SettlementDiffItem.ADJUSTED, adjusted.getStatus());
        SettlementBatch closed = service.closeBatch("B1");
        assertEquals(SettlementBatch.CLOSED, closed.getStatus());
        assertEquals(SettlementDiffItem.CLOSED, diff.getStatus());
    }

    @Test
    void returnsPnlReport() {
        SettlementBatch batch = batch(LocalDate.now(), SettlementBatch.REVIEWING);
        batch.setBatchId("B1");
        batch.setTotalPnl(new BigDecimal("12.5"));
        batch.setTotalFee(new BigDecimal("2.1"));
        when(batchMapper.selectByBatchId("B1")).thenReturn(batch);

        Map<String, Object> report = service.report("B1", "pnl");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) report.get("data");
        assertEquals(new BigDecimal("12.5"), data.get("totalPnl"));
        assertEquals(new BigDecimal("2.1"), data.get("totalFee"));
    }

    private static SettlementBatch batch(LocalDate date, String status) {
        SettlementBatch batch = new SettlementBatch();
        batch.setBatchId("B-" + date);
        batch.setTradeDate(date);
        batch.setStatus(status);
        batch.setTotalPnl(BigDecimal.ZERO);
        batch.setTotalFee(BigDecimal.ZERO);
        batch.setTotalDiff(0);
        return batch;
    }

    private static FundAccount account(String id, String total,
                                       String available, String margin) {
        FundAccount account = new FundAccount();
        account.setAccountId(id);
        account.setTotalAsset(new BigDecimal(total));
        account.setAvailableCash(new BigDecimal(available));
        account.setMargin(new BigDecimal(margin));
        return account;
    }

    private static AccountInfo counter(String balance, String available, String margin) {
        AccountInfo info = new AccountInfo();
        info.setBalance(new BigDecimal(balance));
        info.setAvailable(new BigDecimal(available));
        info.setMargin(new BigDecimal(margin));
        return info;
    }

    private static TradeRecord trade(String accountId) {
        TradeRecord trade = new TradeRecord();
        trade.setTradeId("T");
        trade.setAccountId(accountId);
        trade.setCommission(new BigDecimal("2.1"));
        trade.setRealizedPnl(new BigDecimal("10"));
        trade.setTurnover(new BigDecimal("7000"));
        trade.setTradeTime(LocalDateTime.now());
        return trade;
    }

    private static TradeReport tradeReport(LocalDate date) {
        TradeReport report = new TradeReport();
        report.setTradeId("T");
        report.setTurnover(new BigDecimal("7000"));
        report.setTradeTime(date.atTime(10, 0));
        return report;
    }

    private static Position position(String accountId, String symbol, String quantity) {
        Position position = new Position();
        position.setAccountId(accountId);
        position.setSymbol(symbol);
        position.setDirection("LONG");
        position.setQuantity(new BigDecimal(quantity));
        return position;
    }

    private static PositionDetail gatewayPosition(String symbol, String quantity) {
        PositionDetail position = new PositionDetail();
        position.setSymbol(symbol);
        position.setDirection("LONG");
        position.setTotalVolume(new BigDecimal(quantity));
        return position;
    }

    private static FundFlow flow(String type, String amount) {
        FundFlow flow = new FundFlow();
        flow.setBizType(type);
        flow.setChangeAmount(new BigDecimal(amount));
        return flow;
    }
}
