package com.lhjypt.core.service.settlement;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import com.lhjypt.core.model.trade.PositionDetail;
import com.lhjypt.core.model.trade.TradeRecord;
import com.lhjypt.core.model.trade.TradeReport;
import com.lhjypt.core.model.fund.FundFlow;
import com.lhjypt.core.service.trade.gateway.GatewayRouter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 日终清算、内外对账和差异工单。
 */
@Service
@RequiredArgsConstructor
public class DailySettlementService {

    private static final Logger log = LoggerFactory.getLogger(DailySettlementService.class);

    private final SettlementBatchMapper batchMapper;
    private final SettlementDiffItemMapper diffMapper;
    private final TradeRecordMapper tradeRecordMapper;
    private final FundAccountMapper fundAccountMapper;
    private final FundFlowMapper fundFlowMapper;
    private final PositionMapper positionMapper;
    private final GatewayRouter gatewayRouter;

    @Value("${lhjypt.settlement.tolerance:0.01}")
    private BigDecimal tolerance;

    public SettlementBatch run(LocalDate tradeDate) {
        return run(tradeDate, false);
    }

    public SettlementBatch run(LocalDate tradeDate, boolean force) {
        LocalDate date = tradeDate == null ? LocalDate.now() : tradeDate;
        if (date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("不能对未来交易日执行清算: " + date);
        }

        SettlementBatch batch = batchMapper.selectByTradeDate(date);
        if (batch != null && SettlementBatch.CLOSED.equals(batch.getStatus())) {
            throw new IllegalStateException("该交易日批次已关闭，禁止重跑: " + date);
        }
        if (batch != null && !force) {
            throw new IllegalStateException(
                    "该交易日批次已存在，如需重跑请携带 force=true 和确认令牌: " + date);
        }
        if (batch == null) {
            batch = new SettlementBatch();
            batch.setBatchId("STL-" + date.toString().replace("-", "")
                    + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
            batch.setTradeDate(date);
            batch.setStatus(SettlementBatch.CREATED);
            batch.setAccountCount(0);
            batch.setTotalPnl(BigDecimal.ZERO);
            batch.setTotalFee(BigDecimal.ZERO);
            batch.setTotalDiff(0);
            batch.setCreateTime(LocalDateTime.now());
            batch.setUpdateTime(LocalDateTime.now());
            batchMapper.insert(batch);
        } else {
            diffMapper.delete(new QueryWrapper<SettlementDiffItem>()
                    .eq("batch_id", batch.getBatchId()));
            batch.setStatus(SettlementBatch.CREATED);
            batch.setErrorMessage(null);
            batch.setFinishedAt(null);
            batch.setUpdateTime(LocalDateTime.now());
            batchMapper.updateById(batch);
        }

        batch.setStatus(SettlementBatch.RUNNING);
        batch.setStartedAt(LocalDateTime.now());
        batch.setUpdateTime(LocalDateTime.now());
        batchMapper.updateById(batch);

        try {
            if (!gatewayRouter.isAnyAuthenticated()) {
                throw new IllegalStateException("无可用交易网关，无法执行外部对账");
            }

            List<TradeRecord> trades = tradeRecordMapper.selectByTradeTimeRange(
                    date.atStartOfDay(), date.plusDays(1).atStartOfDay());
            List<FundFlow> flows = fundFlowMapper.selectByTimeRange(
                    date.atStartOfDay(), date.plusDays(1).atStartOfDay());
            BigDecimal totalFee = sumFlow(flows, "COMMISSION", true);
            BigDecimal totalPnl = sumFlow(flows, "REALIZED_PNL", false);
            if (flows.isEmpty()) {
                totalFee = trades.stream()
                        .map(trade -> safe(trade.getCommission()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                totalPnl = trades.stream()
                        .map(trade -> safe(trade.getRealizedPnl()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }

            Set<String> accountIds = new LinkedHashSet<>();
            trades.stream().map(TradeRecord::getAccountId).forEach(accountIds::add);
            List<FundAccount> allAccounts = fundAccountMapper.selectList(null);
            if (accountIds.isEmpty()) {
                allAccounts.stream().map(FundAccount::getAccountId).forEach(accountIds::add);
            }

            List<SettlementDiffItem> diffs = new ArrayList<>();
            reconcileFunds(batch, allAccounts, diffs);
            reconcilePositions(batch, accountIds, diffs);
            reconcileTrades(batch, date, trades, diffs);
            diffs.forEach(diffMapper::insert);

            batch.setAccountCount(accountIds.size());
            batch.setTotalFee(totalFee.setScale(8, RoundingMode.HALF_UP));
            batch.setTotalPnl(totalPnl.setScale(8, RoundingMode.HALF_UP));
            batch.setTotalDiff(diffs.size());
            batch.setStatus(SettlementBatch.REVIEWING);
            batch.setFinishedAt(LocalDateTime.now());
            batch.setUpdateTime(LocalDateTime.now());
            batchMapper.updateById(batch);
            log.info("清算完成: batch={} date={} trades={} diffs={}",
                    batch.getBatchId(), date, trades.size(), diffs.size());
            return batch;
        } catch (Exception error) {
            batch.setStatus(SettlementBatch.FAILED);
            batch.setErrorMessage(truncate(error.getMessage()));
            batch.setFinishedAt(LocalDateTime.now());
            batch.setUpdateTime(LocalDateTime.now());
            batchMapper.updateById(batch);
            log.error("清算失败: batch={} error={}", batch.getBatchId(), error.getMessage());
            throw error;
        }
    }

    public List<SettlementBatch> listBatches(LocalDate tradeDate) {
        return tradeDate == null
                ? batchMapper.selectList(new QueryWrapper<SettlementBatch>()
                        .orderByDesc("create_time"))
                : batchMapper.selectByDate(tradeDate);
    }

    public SettlementBatch getBatch(String batchId) {
        SettlementBatch batch = batchMapper.selectByBatchId(batchId);
        if (batch == null) {
            throw new IllegalArgumentException("清算批次不存在: " + batchId);
        }
        return batch;
    }

    public List<SettlementDiffItem> getDiffs(String batchId) {
        getBatch(batchId);
        return diffMapper.selectByBatchId(batchId);
    }

    public SettlementDiffItem adjustDiff(String diffId, String adjustBy, String note) {
        SettlementDiffItem diff = diffMapper.selectByDiffId(diffId);
        if (diff == null) {
            throw new IllegalArgumentException("差异工单不存在: " + diffId);
        }
        if (SettlementDiffItem.CLOSED.equals(diff.getStatus())) {
            throw new IllegalStateException("差异工单已关闭: " + diffId);
        }
        diff.setStatus(SettlementDiffItem.ADJUSTED);
        diff.setAdjustBy(adjustBy);
        diff.setAdjustNote(note);
        diff.setUpdateTime(LocalDateTime.now());
        diffMapper.updateById(diff);
        return diff;
    }

    public SettlementBatch closeBatch(String batchId) {
        SettlementBatch batch = getBatch(batchId);
        if (SettlementBatch.CLOSED.equals(batch.getStatus())) {
            return batch;
        }
        List<SettlementDiffItem> diffs = diffMapper.selectByBatchId(batchId);
        for (SettlementDiffItem diff : diffs) {
            if (SettlementDiffItem.OPEN.equals(diff.getStatus())
                    || "ADJUSTING".equals(diff.getStatus())) {
                throw new IllegalStateException("存在未处理差异工单，无法关闭批次: " + diff.getDiffId());
            }
            if (SettlementDiffItem.ADJUSTED.equals(diff.getStatus())) {
                diff.setStatus(SettlementDiffItem.CLOSED);
                diff.setUpdateTime(LocalDateTime.now());
                diffMapper.updateById(diff);
            }
        }
        batch.setStatus(SettlementBatch.CLOSED);
        batch.setFinishedAt(LocalDateTime.now());
        batch.setUpdateTime(LocalDateTime.now());
        batchMapper.updateById(batch);
        return batch;
    }

    public Map<String, Object> report(String batchId, String type) {
        SettlementBatch batch = getBatch(batchId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", batchId);
        result.put("type", type);
        switch (type == null ? "pnl" : type.toLowerCase()) {
            case "fund" -> result.put("data", fundAccountMapper.selectList(null));
            case "position" -> result.put("data", positionMapper.selectList(null));
            case "trade" -> result.put("data", tradeRecordMapper.selectByTradeTimeRange(
                    batch.getTradeDate().atStartOfDay(),
                    batch.getTradeDate().plusDays(1).atStartOfDay()));
            case "pnl" -> {
                Map<String, Object> pnl = new LinkedHashMap<>();
                pnl.put("totalPnl", batch.getTotalPnl());
                pnl.put("totalFee", batch.getTotalFee());
                pnl.put("diffCount", batch.getTotalDiff());
                result.put("data", pnl);
            }
            default -> throw new IllegalArgumentException("不支持的报表类型: " + type);
        }
        return result;
    }

    private void reconcileFunds(SettlementBatch batch,
                                List<FundAccount> accounts,
                                List<SettlementDiffItem> diffs) {
        AccountInfo counter = gatewayRouter.queryAccount(null);
        for (FundAccount account : accounts) {
            addDiffIfDifferent(batch, diffs, account.getAccountId(),
                    "fund.total", account.getTotalAsset(), counter.getBalance());
            addDiffIfDifferent(batch, diffs, account.getAccountId(),
                    "fund.available", account.getAvailableCash(), counter.getAvailable());
            addDiffIfDifferent(batch, diffs, account.getAccountId(),
                    "fund.margin", account.getMargin(), counter.getMargin());
        }
    }

    private void reconcilePositions(SettlementBatch batch,
                                    Set<String> accountIds,
                                    List<SettlementDiffItem> diffs) {
        Map<String, BigDecimal> system = new LinkedHashMap<>();
        String diffAccount = accountIds.size() == 1
                ? accountIds.iterator().next() : "ALL";
        for (String accountId : accountIds) {
            for (Position position : positionMapper.selectByAccount(accountId)) {
                system.merge(positionKey(position.getSymbol(), position.getDirection()),
                        safe(position.getQuantity()), BigDecimal::add);
            }
        }

        Map<String, BigDecimal> counter = new LinkedHashMap<>();
        for (PositionDetail position : gatewayRouter.queryPositionsAll()) {
            counter.merge(positionKey(position.getSymbol(), position.getDirection()),
                    safe(position.getTotalVolume()), BigDecimal::add);
        }

        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(system.keySet());
        keys.addAll(counter.keySet());
        for (String key : keys) {
            addDiffIfDifferent(batch, diffs, diffAccount,
                    "position." + key,
                    system.getOrDefault(key, BigDecimal.ZERO),
                    counter.getOrDefault(key, BigDecimal.ZERO));
        }
    }

    private void reconcileTrades(SettlementBatch batch,
                                 LocalDate date,
                                 List<TradeRecord> systemTrades,
                                 List<SettlementDiffItem> diffs) {
        List<TradeReport> counterTrades = gatewayRouter.queryTradesAll().stream()
                .filter(trade -> trade.getTradeTime() != null
                        && trade.getTradeTime().toLocalDate().equals(date))
                .toList();
        BigDecimal systemTurnover = systemTrades.stream()
                .map(trade -> safe(trade.getTurnover()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal counterTurnover = counterTrades.stream()
                .map(trade -> safe(trade.getTurnover()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        addDiffIfDifferent(batch, diffs, "ALL", "trade.count",
                BigDecimal.valueOf(systemTrades.size()),
                BigDecimal.valueOf(counterTrades.size()));
        addDiffIfDifferent(batch, diffs, "ALL", "trade.turnover",
                systemTurnover, counterTurnover);
    }

    private void addDiffIfDifferent(SettlementBatch batch,
                                    List<SettlementDiffItem> diffs,
                                    String accountId, String field,
                                    BigDecimal systemValue,
                                    BigDecimal counterValue) {
        BigDecimal system = safe(systemValue).setScale(8, RoundingMode.HALF_UP);
        BigDecimal counter = safe(counterValue).setScale(8, RoundingMode.HALF_UP);
        BigDecimal difference = system.subtract(counter);
        if (difference.abs().compareTo(tolerance) <= 0) {
            return;
        }
        SettlementDiffItem diff = new SettlementDiffItem();
        diff.setDiffId("DIFF-" + UUID.randomUUID().toString()
                .substring(0, 8).toUpperCase());
        diff.setBatchId(batch.getBatchId());
        diff.setAccountId(accountId);
        diff.setFieldName(field);
        diff.setSystemValue(system);
        diff.setCounterValue(counter);
        diff.setDiffValue(difference);
        diff.setTolerance(tolerance);
        diff.setStatus(SettlementDiffItem.OPEN);
        diff.setCreateTime(LocalDateTime.now());
        diff.setUpdateTime(LocalDateTime.now());
        diffs.add(diff);
    }

    private static String positionKey(String symbol, String direction) {
        return symbol + "." + direction;
    }

    private static BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String truncate(String message) {
        if (message == null) {
            return "unknown settlement error";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private static BigDecimal sumFlow(List<FundFlow> flows,
                                      String bizType,
                                      boolean absolute) {
        return flows.stream()
                .filter(flow -> bizType.equals(flow.getBizType()))
                .map(flow -> absolute
                        ? safe(flow.getChangeAmount()).abs()
                        : safe(flow.getChangeAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
