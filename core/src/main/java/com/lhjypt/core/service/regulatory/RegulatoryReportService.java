package com.lhjypt.core.service.regulatory;

import com.alibaba.fastjson2.JSON;
import com.lhjypt.core.mapper.AmlRuleMapper;
import com.lhjypt.core.mapper.RegulatoryReportBatchMapper;
import com.lhjypt.core.mapper.RegulatoryReportItemMapper;
import com.lhjypt.core.mapper.TradeRecordMapper;
import com.lhjypt.core.model.regulatory.AmlRule;
import com.lhjypt.core.model.regulatory.RegulatoryReportBatch;
import com.lhjypt.core.model.regulatory.RegulatoryReportItem;
import com.lhjypt.core.model.trade.TradeRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 监管报送批次服务（P2 模块四）。
 *
 * <p>支持两种报表：
 * <ul>
 *   <li>{@code DAILY_TRADE}：成交日报（当日全部成交明细）；</li>
 *   <li>{@code LARGE_TRADE}：大额交易报备（成交额 ≥ 大额阈值，阈值取自 AML LARGE_SINGLE 规则，缺省 50 万）。</li>
 * </ul>
 * 批次状态机 GENERATING→READY→SUBMITTED；同 {reportType, reportDate} 幂等，重复运行返回既有批次。
 */
@Service
@RequiredArgsConstructor
public class RegulatoryReportService {

    /** 大额报备缺省阈值（CNY），可配置到 AML LARGE_SINGLE 规则。 */
    private static final BigDecimal DEFAULT_LARGE_THRESHOLD = new BigDecimal("500000");

    public static final String TYPE_LARGE_TRADE = "LARGE_TRADE";
    public static final String TYPE_DAILY_TRADE = "DAILY_TRADE";

    private final RegulatoryReportBatchMapper batchMapper;
    private final RegulatoryReportItemMapper itemMapper;
    private final TradeRecordMapper tradeRecordMapper;
    private final AmlRuleMapper amlRuleMapper;

    @Transactional
    public RegulatoryReportBatch run(String reportType, LocalDate reportDate, String operator) {
        String type = normalizeType(reportType);
        LocalDate date = reportDate == null ? LocalDate.now() : reportDate;

        RegulatoryReportBatch existing = batchMapper.selectByTypeAndDate(type, date);
        if (existing != null && !RegulatoryReportBatch.FAILED.equals(existing.getStatus())) {
            return existing; // 幂等：READY/SUBMITTED/GENERATING 直接返回
        }

        List<TradeRecord> trades = tradeRecordMapper.selectByTradingDay(date.toString());
        if (trades.isEmpty()) {
            throw new IllegalStateException("该交易日无成交记录，无法生成报送: " + type + "@" + date);
        }

        BigDecimal threshold = largeThreshold();
        List<TradeRecord> included = switch (type) {
            case TYPE_LARGE_TRADE ->
                    trades.stream().filter(t -> t.getTurnover() != null
                            && t.getTurnover().compareTo(threshold) >= 0).toList();
            default -> trades;
        };
        if (included.isEmpty()) {
            throw new IllegalStateException("所取成交均为记录未达大额阈值(" + threshold + ")，无需报送");
        }

        RegulatoryReportBatch batch;
        if (existing == null) {
            batch = new RegulatoryReportBatch();
            batch.setBatchId("REG-" + type + "-" + date + "-"
                    + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
            batch.setCreateTime(LocalDateTime.now());
        } else {
            batch = existing;
            itemMapper.deleteByBatchId(batch.getBatchId());
        }
        batch.setReportType(type);
        batch.setReportDate(date);
        batch.setStatus(RegulatoryReportBatch.READY);
        batch.setUpdateTime(LocalDateTime.now());

        BigDecimal total = BigDecimal.ZERO;
        int row = 0;
        for (TradeRecord trade : included) {
            total = total.add(safe(trade.getTurnover()));
            RegulatoryReportItem item = new RegulatoryReportItem();
            item.setBatchId(batch.getBatchId());
            item.setRowNo(++row);
            item.setContentJson(JSON.toJSONString(rowMap(row, trade)));
            item.setStatus("READY");
            item.setCreateTime(LocalDateTime.now());
            itemMapper.insert(item);
        }
        batch.setTotalItems(row);
        batch.setTotalAmount(total);
        batch.setFileName(type.toLowerCase(Locale.ROOT) + "-" + date + ".json");
        if (batch.getId() == null) {
            batchMapper.insert(batch);
        } else {
            batchMapper.updateById(batch);
        }
        return batch;
    }

    public List<RegulatoryReportBatch> list() {
        return batchMapper.selectAll();
    }

    public Map<String, Object> detail(String batchId) {
        RegulatoryReportBatch batch = batchMapper.selectByBatchId(batchId);
        if (batch == null) {
            throw new IllegalArgumentException("报送批次不存在: " + batchId);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batch", batch);
        result.put("items", itemMapper.selectByBatchId(batchId));
        return result;
    }

    @Transactional
    public RegulatoryReportBatch submit(String batchId, String operator) {
        RegulatoryReportBatch batch = batchMapper.selectByBatchId(batchId);
        if (batch == null) {
            throw new IllegalArgumentException("报送批次不存在: " + batchId);
        }
        if (RegulatoryReportBatch.SUBMITTED.equals(batch.getStatus())) {
            return batch;
        }
        if (!RegulatoryReportBatch.READY.equals(batch.getStatus())) {
            throw new IllegalStateException("仅 READY 批次可报送，当前 " + batch.getStatus());
        }
        batch.setStatus(RegulatoryReportBatch.SUBMITTED);
        batch.setSubmitBy(operator);
        batch.setSubmitTime(LocalDateTime.now());
        batch.setUpdateTime(LocalDateTime.now());
        batchMapper.updateById(batch);
        return batch;
    }

    private BigDecimal largeThreshold() {
        AmlRule rule = amlRuleMapper.selectEnabledByCode(AmlRule.LARGE_SINGLE);
        return rule != null && rule.getThreshold() != null
                ? rule.getThreshold() : DEFAULT_LARGE_THRESHOLD;
    }

    private Map<String, Object> rowMap(int rowNo, TradeRecord trade) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("rowNo", rowNo);
        row.put("tradeId", trade.getTradeId());
        row.put("orderId", trade.getOrderId());
        row.put("accountId", trade.getAccountId());
        row.put("strategyId", trade.getStrategyId());
        row.put("symbol", trade.getSymbol());
        row.put("exchange", trade.getExchange());
        row.put("direction", trade.getDirection());
        row.put("offsetFlag", trade.getOffsetFlag());
        row.put("quantity", trade.getQuantity());
        row.put("price", trade.getPrice());
        row.put("turnover", trade.getTurnover());
        row.put("commission", trade.getCommission());
        row.put("tradeTime", trade.getTradeTime());
        return row;
    }

    private static String normalizeType(String reportType) {
        if (reportType == null || reportType.isBlank()) {
            throw new IllegalArgumentException("reportType 不能为空");
        }
        String value = reportType.trim().toUpperCase(Locale.ROOT);
        if (!TYPE_LARGE_TRADE.equals(value) && !TYPE_DAILY_TRADE.equals(value)) {
            throw new IllegalArgumentException(
                    "reportType 必须是 LARGE_TRADE/DAILY_TRADE 之一: " + reportType);
        }
        return value;
    }

    private static BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}