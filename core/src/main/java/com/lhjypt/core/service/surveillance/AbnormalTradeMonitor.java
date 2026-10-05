package com.lhjypt.core.service.surveillance;

import com.lhjypt.core.mapper.AbnormalTradeAlertMapper;
import com.lhjypt.core.mapper.OrderMapper;
import com.lhjypt.core.model.Order;
import com.lhjypt.core.model.surveillance.AbnormalTradeAlert;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 异常交易监控（P1，TASK-P1-003）
 *
 * 四类监管规则滑动窗口评分（数据源：orders 表委托流，本地模式秒级扫描代替 Kafka）：
 * - SELF_TRADE  自成交：同一策略同标的窗口内买卖双向均有成交
 * - FREQ_CANCEL 频繁撤单：CANCELLED 占窗口内订单比率
 * - FALSE_ORDER 虚假申报：报撤比（未成交即撤）异常高
 * - PUMP_DUMP   拉抬打压：同标的成交均价窗口内变动率
 *
 * 评分 → severity（≥0.9 CRITICAL / ≥0.6 WARN / 否则 INFO）；
 * 幂等：同一 (规则,账户,策略,标的) 已有 OPEN 告警则去重不再新增。
 * CRITICAL 告警的处置建议：联动事中风控限制开仓（由扫描调用方决定是否下发 BLOCK 规则）。
 */
@Service
@RequiredArgsConstructor
public class AbnormalTradeMonitor {

    private static final Logger log = LoggerFactory.getLogger(AbnormalTradeMonitor.class);

    /** 评分阈值：低于该值不生成告警 */
    static final double ALERT_SCORE_THRESHOLD = 0.5;
    /** 默认滑动窗口（秒） */
    static final int DEFAULT_WINDOW_SEC = 300;

    private final OrderMapper orderMapper;
    private final AbnormalTradeAlertMapper alertMapper;

    /** 规则阈值（可由 API 调整，默认对齐 PRD：撤单率 60%） */
    @Getter
    private final Map<String, Double> thresholds = defaultThresholds();

    private static Map<String, Double> defaultThresholds() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("SELF_TRADE", 0.5);        // 自成交：出现即高危，评分=笔数密度
        m.put("FREQ_CANCEL", 0.6);       // 撤单率阈值 60%
        m.put("FALSE_ORDER", 0.7);       // 报撤比阈值 70%
        m.put("PUMP_DUMP", 0.03);        // 窗口内价格变动率阈值 3%
        return m;
    }

    public void updateThreshold(String ruleCode, double value) {
        if (!thresholds.containsKey(ruleCode)) {
            throw new IllegalArgumentException("未知规则: " + ruleCode);
        }
        thresholds.put(ruleCode, value);
        log.info("异常交易规则阈值更新: {}={}", ruleCode, value);
    }

    /**
     * 全量扫描：对滑动窗口内的委托/成交做四类评分，命中生成告警。
     * 返回本次新增的告警列表（去重命中不算新增）。
     */
    public List<AbnormalTradeAlert> scanWindow(int windowSec) {        LocalDateTime since = LocalDateTime.now().minusSeconds(windowSec);
        List<Order> orders = orderMapper.selectCreatedSince(since);
        if (orders.isEmpty()) return List.of();

        List<AbnormalTradeAlert> newAlerts = new ArrayList<>();
        scoreSelfTrade(orders, newAlerts);
        scoreFreqCancel(orders, windowSec, newAlerts);
        scoreFalseOrder(orders, newAlerts);
        scorePumpDump(orders, windowSec, newAlerts);

        if (!newAlerts.isEmpty()) {
            log.info("异常交易扫描: 窗口{}s 订单{}笔 新增告警{}条",
                    windowSec, orders.size(), newAlerts.size());
        }
        return newAlerts;
    }

    /**
     * 周期性扫描（本地模式：以 orders 表轮询代替 Kafka 流式消费，30s 间隔满足分钟级监管识别）
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30_000, initialDelay = 45_000)
    public void scheduledScan() {
        try {
            scanWindow(DEFAULT_WINDOW_SEC);
        } catch (Exception e) {
            log.warn("异常交易周期扫描失败（不阻塞交易）: {}", e.getMessage());
        }
    }

    // ---------- SELF_TRADE ----------

    /** 同一策略×标的：窗口内买入成交与卖出成交同时存在 → 自成交 */
    void scoreSelfTrade(List<Order> orders, List<AbnormalTradeAlert> out) {
        // key: strategy|symbol -> 买成交量 / 卖成交量
        Map<String, BigDecimal[]> sides = new HashMap<>();
        for (Order o : orders) {
            BigDecimal traded = nvl(o.getFilledQuantity());
            if (traded.compareTo(BigDecimal.ZERO) <= 0 || o.getStrategyId() == null) continue;
            String key = o.getStrategyId() + "|" + o.getSymbol();
            BigDecimal[] bs = sides.computeIfAbsent(key,
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if ("BUY".equals(o.getDirection())) bs[0] = bs[0].add(traded);
            else bs[1] = bs[1].add(traded);
        }
        for (Map.Entry<String, BigDecimal[]> e : sides.entrySet()) {
            BigDecimal buy = e.getValue()[0];
            BigDecimal sell = e.getValue()[1];
            if (buy.signum() <= 0 || sell.signum() <= 0) continue;
            String[] parts = e.getKey().split("\\|", 2);
            String strategyId = parts[0];
            String symbol = parts[1];
            // 评分：重叠量占小边比例（全重叠=1.0），密度放大到 [0,1]
            BigDecimal overlap = buy.min(sell);
            double score = 0.5 + 0.5 * clamp(overlap.doubleValue()
                    / buy.add(sell).doubleValue() * 2);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("buyFilled", plain(buy));
            detail.put("sellFilled", plain(sell));
            detail.put("overlap", plain(overlap));
            raise(out, "SELF_TRADE", null, strategyId, symbol, score,
                    detail, "自成交：策略" + strategyId + " 在 " + symbol
                            + " 双向成交 买" + plain(buy) + "/卖" + plain(sell));
        }
    }

    // ---------- FREQ_CANCEL ----------

    /** 撤单率 = CANCELLED / 窗口内订单总数（按 策略×标的 聚合） */
    void scoreFreqCancel(List<Order> orders, int windowSec, List<AbnormalTradeAlert> out) {
        Map<String, int[]> agg = new HashMap<>(); // total, cancelled
        for (Order o : orders) {
            if (o.getStrategyId() == null) continue;
            String key = o.getStrategyId() + "|" + o.getSymbol();
            int[] c = agg.computeIfAbsent(key, k -> new int[2]);
            c[0]++;
            if (isCanceled(o.getStatus())) c[1]++;
        }
        double threshold = thresholds.get("FREQ_CANCEL");
        for (Map.Entry<String, int[]> e : agg.entrySet()) {
            int total = e.getValue()[0];
            int cancelled = e.getValue()[1];
            if (total < 5) continue; // 样本过小不判
            double rate = (double) cancelled / total;
            if (rate < threshold) continue;
            String[] parts = e.getKey().split("\\|", 2);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("total", total);
            detail.put("cancelled", cancelled);
            detail.put("rate", round4(rate));
            detail.put("windowSec", windowSec);
            raise(out, "FREQ_CANCEL", null, parts[0], parts[1],
                    clamp(0.5 + (rate - threshold) / threshold), detail,
                    String.format("频繁撤单：策略%s 在 %s 窗口%ds撤单率%.0f%%（%d/%d）",
                            parts[0], parts[1], windowSec, rate * 100, cancelled, total));
        }
    }

    // ---------- FALSE_ORDER ----------

    /** 虚假申报：大额限价单未成交即撤（终态 FILLED 之外按未成交量占比计） */
    void scoreFalseOrder(List<Order> orders, List<AbnormalTradeAlert> out) {
        // 按策略聚合：撤单且零成交的申报量 / 总申报量
        Map<String, BigDecimal[]> agg = new HashMap<>(); // [allQty, fakeQty]
        for (Order o : orders) {
            if (o.getStrategyId() == null) continue;
            BigDecimal[] c = agg.computeIfAbsent(o.getStrategyId(),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal qty = nvl(o.getQuantity());
            c[0] = c[0].add(qty);
            boolean cancelledZeroFill = isCanceled(o.getStatus())
                    && nvl(o.getFilledQuantity()).signum() == 0;
            if (cancelledZeroFill) c[1] = c[1].add(qty);
        }
        double threshold = thresholds.get("FALSE_ORDER");
        for (Map.Entry<String, BigDecimal[]> e : agg.entrySet()) {
            BigDecimal all = e.getValue()[0];
            BigDecimal fake = e.getValue()[1];
            if (all.signum() <= 0) continue;
            double ratio = fake.doubleValue() / all.doubleValue();
            if (ratio < threshold) continue;
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("declaredQty", plain(all));
            detail.put("cancelledUnfilledQty", plain(fake));
            detail.put("ratio", round4(ratio));
            raise(out, "FALSE_ORDER", null, e.getKey(), null,
                    clamp(0.5 + (ratio - threshold) / (1 - threshold) * 0.5), detail,
                    String.format("虚假申报：策略%s 零成交撤单申报量占比%.0f%%",
                            e.getKey(), ratio * 100));
        }
    }

    // ---------- PUMP_DUMP ----------

    /** 拉抬打压：同标的成交均价窗口内首末变动率超阈值 */
    void scorePumpDump(List<Order> orders, int windowSec, List<AbnormalTradeAlert> out) {
        Map<String, List<Order>> bySymbol = new HashMap<>();
        for (Order o : orders) {
            if (nvl(o.getFilledQuantity()).signum() > 0 && o.getAvgFillPrice() != null
                    && o.getAvgFillPrice().signum() > 0) {
                bySymbol.computeIfAbsent(o.getSymbol(), k -> new ArrayList<>()).add(o);
            }
        }
        double threshold = thresholds.get("PUMP_DUMP");
        for (Map.Entry<String, List<Order>> e : bySymbol.entrySet()) {
            List<Order> list = e.getValue();
            if (list.size() < 2) continue;
            BigDecimal first = list.get(0).getAvgFillPrice();
            BigDecimal last = list.get(list.size() - 1).getAvgFillPrice();
            if (first == null || last == null || first.signum() <= 0) continue;
            double change = last.subtract(first)
                    .divide(first, 6, RoundingMode.HALF_UP).doubleValue();
            if (Math.abs(change) < threshold) continue;
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("firstPrice", plain(first));
            detail.put("lastPrice", plain(last));
            detail.put("change", round4(change));
            detail.put("windowSec", windowSec);
            String mode = change > 0 ? "拉抬" : "打压";
            // 任一策略方向与异动一致的订单归因到该策略（取最大成交量策略）
            String strategy = dominantStrategy(list, change > 0 ? "BUY" : "SELL");
            raise(out, "PUMP_DUMP", null, strategy, e.getKey(),
                    clamp(0.5 + (Math.abs(change) - threshold) / threshold * 0.5), detail,
                    String.format("%s：%s 窗口%ds成交均价变动%.2f%%（%s→%s）",
                            mode, e.getKey(), windowSec, change * 100, plain(first), plain(last)));
        }
    }

    private String dominantStrategy(List<Order> orders, String direction) {
        BigDecimal best = BigDecimal.ZERO;
        String bestStrategy = null;
        Map<String, BigDecimal> sum = new HashMap<>();
        for (Order o : orders) {
            if (!direction.equals(o.getDirection()) || o.getStrategyId() == null) continue;
            BigDecimal v = sum.merge(o.getStrategyId(), nvl(o.getFilledQuantity()), BigDecimal::add);
            if (v.compareTo(best) > 0) { best = v; bestStrategy = o.getStrategyId(); }
        }
        return bestStrategy;
    }

    // ---------- 告警生成（幂等去重） ----------

    private void raise(List<AbnormalTradeAlert> out, String ruleCode, String accountId,
                       String strategyId, String symbol, double score,
                       Map<String, Object> detail, String message) {
        if (score < ALERT_SCORE_THRESHOLD) return;
        if (hasOpenAlert(ruleCode, strategyId, symbol)) return; // 幂等：同维度 OPEN 告警去重

        AbnormalTradeAlert alert = new AbnormalTradeAlert();
        alert.setAlertId("AT-" + UUID.randomUUID().toString()
                .substring(0, 8).toUpperCase());
        alert.setRuleCode(ruleCode);
        alert.setAccountId(accountId);
        alert.setStrategyId(strategyId);
        alert.setSymbol(symbol);
        alert.setScore(BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP));
        alert.setDetailJson(toJson(detail, message));
        alert.setSeverity(severity(score));
        alert.setStatus("OPEN");
        alert.setCreateTime(LocalDateTime.now());
        alert.setUpdateTime(LocalDateTime.now());
        alertMapper.insert(alert);
        out.add(alert);
    }

    private boolean hasOpenAlert(String ruleCode, String strategyId, String symbol) {
        for (AbnormalTradeAlert open : alertMapper.selectOpen()) {
            if (!ruleCode.equals(open.getRuleCode())) continue;
            if (!eq(open.getStrategyId(), strategyId)) continue;
            if (!eq(open.getSymbol(), symbol)) continue;
            return true;
        }
        return false;
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    static String severity(double score) {
        if (score >= 0.9) return "CRITICAL";
        if (score >= ALERT_SCORE_THRESHOLD) return "WARN";
        return "INFO";
    }

    private static String toJson(Map<String, Object> detail, String message) {
        StringBuilder sb = new StringBuilder("{");
        Map<String, Object> all = new LinkedHashMap<>(detail);
        all.put("message", message);
        boolean first = true;
        for (Map.Entry<String, Object> e : all.entrySet()) {
            if (!first) sb.append(',');
            sb.append('"').append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof Number) sb.append(v);
            else sb.append('"').append(String.valueOf(v).replace("\"", "\\\"")).append('"');
            first = false;
        }
        return sb.append('}').toString();
    }

    // ---------- helpers ----------

    /** 撤单终态判定：兼容 OMS 枚举 CANCELED 与常见拼写 CANCELLED */
    private static boolean isCanceled(String status) {
        return "CANCELED".equals(status) || "CANCELLED".equals(status);
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private static double round4(double v) {
        return Math.round(v * 10000) / 10000.0;
    }
}
