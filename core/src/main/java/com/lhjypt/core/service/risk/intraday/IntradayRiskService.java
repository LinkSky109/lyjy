package com.lhjypt.core.service.risk.intraday;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lhjypt.core.mapper.FundAccountMapper;
import com.lhjypt.core.mapper.IntradayRiskRuleMapper;
import com.lhjypt.core.mapper.RiskDispositionLogMapper;
import com.lhjypt.core.model.FundAccount;
import com.lhjypt.core.model.risk.IntradayRiskRule;
import com.lhjypt.core.model.risk.RiskDispositionLog;
import com.lhjypt.core.model.trade.PositionDetail;
import com.lhjypt.core.model.trade.TradeRequest;
import com.lhjypt.core.model.var.VarCalculation;
import com.lhjypt.core.service.risk.OpenBlockRegistry;
import com.lhjypt.core.service.risk.PreTradeRiskService;
import com.lhjypt.core.service.trade.TradeExecutionService;
import com.lhjypt.core.service.var.VarService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事中风控服务（P1）
 *
 * 与事前风控的分工：
 * - 事前（PreTradeRiskService）：下单瞬间的静态规则校验（持仓/资金/频率/黑白名单/自成交）
 * - 事中（本服务）：交易进行中的实时动态监控与处置（持仓超限/日内亏损/资金不足/订单失控）
 *
 * 评估触发：定时（默认5秒）扫描持仓与资金快照；处置链 WARN → BLOCK_OPEN → FORCE_CLOSE。
 * 处置去重：同规则同作用域在 cooldownSec 内不重复处置（进程内冷却表，对齐 P0 无外部依赖做法）。
 * 联动：BLOCK 处置写入 OpenBlockRegistry，事前风控下单校验时读取并拦截新开仓。
 */
@Service
@RequiredArgsConstructor
public class IntradayRiskService {
    private static final Logger log = LoggerFactory.getLogger(IntradayRiskService.class);

    private final IntradayRiskRuleMapper ruleMapper;
    private final RiskDispositionLogMapper dispositionLogMapper;
    private final TradeExecutionService tradeExecutionService;
    private final FundAccountMapper fundAccountMapper;
    private final PreTradeRiskService preTradeRiskService;
    private final OpenBlockRegistry openBlockRegistry;
    private final com.lhjypt.core.common.auth.ConfirmTokenService confirmTokenService;
    private final VarService varService;

    /** 冷却表：ruleId|scopeType|scopeId -> 最近处置时间(ms) */
    private final Map<String, Long> cooldownMap = new ConcurrentHashMap<>();

    /** 定时评估所有启用的事中规则 */
    @Scheduled(fixedRateString = "${lhjypt.risk.intraday.eval-interval:5000}")
    public void scheduledEvaluate() {
        try {
            evaluateAll();
        } catch (Exception e) {
            // 事中评估异常不阻断交易主链路
            log.warn("事中风控评估异常: {}", e.getMessage());
        }
    }

    /**
     * 全量评估（持仓+资金）。
     * 持仓来自柜台会话（网关未连接时为空，跳过持仓维度）；资金逐账户遍历评估。
     */
    public List<RiskDispositionLog> evaluateAll() {
        List<RiskDispositionLog> dispositions = new ArrayList<>();
        List<IntradayRiskRule> rules = ruleMapper.selectEnabledRules();

        List<PositionDetail> positions = safeQueryPositions();
        List<FundAccount> accounts = safeQueryAccounts();

        for (IntradayRiskRule rule : rules) {
            JSONObject params = parseParams(rule.getThresholdParams());
            switch (rule.getRuleType()) {
                case "POSITION_LIMIT":
                    evaluatePositionLimit(rule, params, positions, dispositions);
                    break;
                case "LOSS_LIMIT":
                    evaluateLossLimit(rule, params, accounts, dispositions);
                    break;
                case "FUND_RISK":
                    evaluateFundRisk(rule, params, accounts, dispositions);
                    break;
                case "ORDER_RATE":
                    evaluateOrderRate(rule, params, dispositions);
                    break;
                case "VAR_LIMIT":
                    evaluateVarLimit(rule, params, dispositions);
                    break;
                default:
                    break;
            }
        }
        return dispositions;
    }

    /**
     * 持仓限额：单标的持仓绝对数量 / 持仓市值占组合市值比例
     * 参数：{"maxVolume":100,"maxRatio":0.2}
     */
    private void evaluatePositionLimit(IntradayRiskRule rule, JSONObject params,
                                       List<PositionDetail> positions,
                                       List<RiskDispositionLog> out) {
        if (positions.isEmpty()) return;
        BigDecimal maxRatio = params.getBigDecimal("maxRatio");
        BigDecimal maxVolume = params.getBigDecimal("maxVolume");
        BigDecimal totalValue = BigDecimal.ZERO;
        for (PositionDetail p : positions) totalValue = totalValue.add(positionValue(p));

        for (PositionDetail p : positions) {
            if (!matchSymbol(rule, p.getSymbol())) continue;
            boolean hit = false;
            String reason = null;
            if (maxVolume != null && p.getTotalVolume().compareTo(maxVolume) > 0) {
                hit = true;
                reason = String.format("标的%s持仓%s，超过限额%s",
                        p.getSymbol(), plain(p.getTotalVolume()), plain(maxVolume));
            }
            if (!hit && maxRatio != null && totalValue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal ratio = positionValue(p).divide(totalValue, 6, RoundingMode.HALF_UP);
                if (ratio.compareTo(maxRatio) > 0) {
                    hit = true;
                    reason = String.format("标的%s持仓占比%.2f%%，超过限额%.2f%%",
                            p.getSymbol(), pct(ratio), pct(maxRatio));
                }
            }
            if (hit) recordDispose(out, rule, "SYMBOL", p.getSymbol(), reason);
        }
    }

    /**
     * 日内亏损限额：浮动+已实现亏损合计超过绝对额或比例
     * 参数：{"maxLoss":50000,"maxLossRatio":0.05}
     */
    private void evaluateLossLimit(IntradayRiskRule rule, JSONObject params,
                                   List<FundAccount> accounts, List<RiskDispositionLog> out) {
        BigDecimal maxLoss = params.getBigDecimal("maxLoss");
        BigDecimal maxLossRatio = params.getBigDecimal("maxLossRatio");
        for (FundAccount account : accounts) {
            if (!matchesAccountScope(rule, account.getAccountId())) continue;
            BigDecimal dailyLoss = account.getUnrealizedPnl()
                    .add(account.getRealizedPnl()).negate(); // 盈亏为负=亏损
            if (dailyLoss.compareTo(BigDecimal.ZERO) <= 0) continue;

            boolean hit = false;
            String reason = null;
            if (maxLoss != null && dailyLoss.compareTo(maxLoss) > 0) {
                hit = true;
                reason = String.format("账户%s日内亏损%s，超过限额%s",
                        account.getAccountId(), plain(dailyLoss), plain(maxLoss));
            }
            if (!hit && maxLossRatio != null && account.getTotalAsset().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal lossRatio = dailyLoss.divide(account.getTotalAsset(), 6, RoundingMode.HALF_UP);
                if (lossRatio.compareTo(maxLossRatio) > 0) {
                    hit = true;
                    reason = String.format("账户%s日内亏损占比%.2f%%，超过限额%.2f%%",
                            account.getAccountId(), pct(lossRatio), pct(maxLossRatio));
                }
            }
            if (hit) recordDispose(out, rule, "ACCOUNT", account.getAccountId(), reason);
        }
    }

    /**
     * 资金风险：可用资金/总资产低于留存线（穿仓预警）
     * 参数：{"minAvailableRatio":0.1}
     */
    private void evaluateFundRisk(IntradayRiskRule rule, JSONObject params,
                                  List<FundAccount> accounts, List<RiskDispositionLog> out) {
        BigDecimal minRatio = params.getBigDecimal("minAvailableRatio");
        if (minRatio == null) minRatio = BigDecimal.ZERO;
        for (FundAccount account : accounts) {
            if (!matchesAccountScope(rule, account.getAccountId())) continue;
            if (account.getTotalAsset().compareTo(BigDecimal.ZERO) <= 0) {
                recordDispose(out, rule, "ACCOUNT", account.getAccountId(),
                        String.format("账户%s总资产为0，触发资金风险", account.getAccountId()));
                continue;
            }
            BigDecimal availableRatio = account.getAvailableCash()
                    .divide(account.getTotalAsset(), 6, RoundingMode.HALF_UP);
            if (availableRatio.compareTo(minRatio) < 0) {
                recordDispose(out, rule, "ACCOUNT", account.getAccountId(),
                        String.format("账户%s可用资金占比%.2f%%，低于留存线%.2f%%",
                                account.getAccountId(), pct(availableRatio), pct(minRatio)));
            }
        }
    }

    /**
     * 订单失控：策略滑动窗口下单次数超阈
     * 参数：{"maxOrdersPerMinute":60}
     */
    private void evaluateOrderRate(IntradayRiskRule rule, JSONObject params,
                                   List<RiskDispositionLog> out) {
        Integer maxOrders = params.getInteger("maxOrdersPerMinute");
        if (maxOrders == null) return;
        String strategyId = (rule.getStrategyId() == null || rule.getStrategyId().isEmpty())
                ? "DEFAULT" : rule.getStrategyId();
        int count = preTradeRiskService.countOrders(strategyId, 60);
        if (count > maxOrders) {
            recordDispose(out, rule, "STRATEGY", strategyId,
                    String.format("策略%s每分钟下单%d笔，超过限制%d笔", strategyId, count, maxOrders));
        }
    }

    /**
     * VaR 限额：读取最近一次 VaR（TASK-P1-008），超过阈值触发处置。
     * 参数：{"maxVar95":100000,"scopeType":"ACCOUNT","scopeId":"DEFAULT-001"}
     * 未显式指定作用域时按规则 accountId/strategyId 推导。
     */
    private void evaluateVarLimit(IntradayRiskRule rule, JSONObject params,
                                  List<RiskDispositionLog> out) {
        BigDecimal maxVar = params.getBigDecimal("maxVar95");
        if (maxVar == null) {
            maxVar = params.getBigDecimal("maxVar");
        }
        if (maxVar == null) return;

        String scopeType = params.getString("scopeType");
        String scopeId = params.getString("scopeId");
        if (scopeType == null || scopeType.isEmpty() || scopeId == null || scopeId.isEmpty()) {
            if (rule.getAccountId() != null && !rule.getAccountId().isEmpty()) {
                scopeType = "ACCOUNT";
                scopeId = rule.getAccountId();
            } else if (rule.getStrategyId() != null && !rule.getStrategyId().isEmpty()) {
                scopeType = "STRATEGY";
                scopeId = rule.getStrategyId();
            } else {
                return;
            }
        }

        VarCalculation latest = varService.latestRaw(scopeType, scopeId);
        if (latest == null || latest.getVarValue() == null) return;
        if (latest.getVarValue().compareTo(maxVar) <= 0) return;

        recordDispose(out, rule, scopeType, scopeId,
                String.format("%s:%s VaR=%s 超过限额%s", scopeType, scopeId,
                        plain(latest.getVarValue()), plain(maxVar)));
    }

    /** 执行处置并把非空结果加入列表（冷却期内 dispose 返回 null） */
    private void recordDispose(List<RiskDispositionLog> out, IntradayRiskRule rule,
                               String scopeType, String scopeId, String reason) {
        RiskDispositionLog d = dispose(rule, scopeType, scopeId, reason);
        if (d != null) out.add(d);
    }

    /**
     * 生成处置：去重冷却 + 落库 + 按级别执行（WARN/BLOCK/FORCE_CLOSE）
     */
    private RiskDispositionLog dispose(IntradayRiskRule rule, String scopeType, String scopeId, String reason) {
        String cooldownKey = rule.getRuleId() + "|" + scopeType + "|" + scopeId;
        long now = System.currentTimeMillis();
        int cooldown = rule.getCooldownSec() == null ? 60 : rule.getCooldownSec();
        Long last = cooldownMap.get(cooldownKey);
        if (last != null && now - last < cooldown * 1000L) {
            return null; // 冷却中不重复处置
        }
        cooldownMap.put(cooldownKey, now);

        RiskDispositionLog d = new RiskDispositionLog();
        d.setDispositionId("DSP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        d.setTriggerRule(rule.getRuleId());
        d.setScopeType(scopeType);
        d.setScopeId(scopeId);
        d.setAction(mapAction(rule.getActionLevel()));
        d.setParams(JSON.toJSONString(Map.of("reason", reason, "ruleType", rule.getRuleType())));
        d.setMessage(reason);
        d.setResult("SUCCESS");
        d.setCreateTime(LocalDateTime.now());
        d.setUpdateTime(LocalDateTime.now());

        // FORCE_CLOSE 保持 PENDING，需人工确认后才发平仓指令
        if ("FORCE_CLOSE".equals(rule.getActionLevel())) {
            d.setResult("PENDING");
        }
        dispositionLogMapper.insert(d);

        switch (rule.getActionLevel()) {
            case "BLOCK":
                openBlockRegistry.block(scopeType, scopeId, cooldown > 0 ? cooldown * 5 : 300);
                log.warn("事中风控[限制开仓] {}:{} - {}", scopeType, scopeId, reason);
                break;
            case "FORCE_CLOSE":
                log.warn("事中风控[强制平仓待确认] {}:{} - {}", scopeType, scopeId, reason);
                break;
            default:
                log.info("事中风控[预警] {}:{} - {}", scopeType, scopeId, reason);
                break;
        }
        return d;
    }

    private String mapAction(String level) {
        return switch (level) {
            case "BLOCK" -> "BLOCK_OPEN";
            case "FORCE_CLOSE" -> "FORCE_CLOSE";
            default -> "WARN";
        };
    }

    /** 查询账户实时风控状态（资金/持仓/限制开仓状态） */
    public Map<String, Object> accountRiskStatus(String accountId) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("accountId", accountId);
        status.put("blocked", openBlockRegistry.isBlocked(accountId, "DEFAULT", null));

        FundAccount account = null;
        try {
            account = fundAccountMapper.selectByAccountId(accountId);
        } catch (Exception ignored) {
        }
        if (account != null) {
            status.put("totalAsset", account.getTotalAsset());
            status.put("availableCash", account.getAvailableCash());
            status.put("unrealizedPnl", account.getUnrealizedPnl());
            status.put("realizedPnl", account.getRealizedPnl());
        }

        List<PositionDetail> positions = safeQueryPositions();
        List<Map<String, Object>> posList = new ArrayList<>();
        for (PositionDetail p : positions) {
            posList.add(Map.of(
                    "symbol", p.getSymbol(),
                    "direction", p.getDirection(),
                    "totalVolume", p.getTotalVolume(),
                    "avgCost", p.getAvgCost(),
                    "positionPnl", p.getPositionPnl()));
        }
        status.put("positions", posList);
        status.put("evaluatedAt", LocalDateTime.now().toString());
        return status;
    }

    /**
     * 人工确认执行强制平仓：必须先通过一次性确认令牌（PRD §3.5 敏感操作二次确认），
     * 再按持仓方向反向平掉可用持仓，处置记录 PENDING → SUCCESS/FAILED
     */
    public RiskDispositionLog confirmForceClose(String dispositionId, String operator, String confirmToken) {
        if (!confirmTokenService.validate(confirmToken)) {
            throw new IllegalStateException("缺少或无效的二次确认令牌，请先调用 /api/auth/confirm-token 获取");
        }
        RiskDispositionLog d = dispositionLogMapper.selectByDispositionId(dispositionId);
        if (d == null) {
            throw new IllegalArgumentException("处置记录不存在: " + dispositionId);
        }
        if (!"FORCE_CLOSE".equals(d.getAction())) {
            throw new IllegalStateException("该处置不是强制平仓，无需确认: " + dispositionId);
        }
        if (!"PENDING".equals(d.getResult())) {
            throw new IllegalStateException("该处置已执行，请勿重复确认: " + dispositionId);
        }

        List<PositionDetail> positions = safeQueryPositions();
        int closedOrders = 0;
        for (PositionDetail p : positions) {
            if (p.getAvailableVolume().compareTo(BigDecimal.ZERO) <= 0) continue;
            if ("SYMBOL".equals(d.getScopeType()) && !d.getScopeId().equals(p.getSymbol())) continue;

            TradeRequest req = new TradeRequest();
            req.setSymbol(p.getSymbol());
            req.setExchange(p.getExchange());
            req.setDirection("LONG".equals(p.getDirection()) ? "SELL" : "BUY");
            req.setOffsetFlag("CLOSE");
            req.setOrderType("MARKET");
            req.setQuantity(p.getAvailableVolume());
            req.setStrategyId("RISK_FORCE_CLOSE");
            req.setAccountId("ACCOUNT".equals(d.getScopeType()) ? d.getScopeId() : "RISK");
            req.setRemark("事中风控强制平仓 " + d.getDispositionId());
            try {
                tradeExecutionService.placeOrder(req);
                closedOrders++;
            } catch (Exception e) {
                log.warn("强制平仓下单失败 {}: {}", p.getSymbol(), e.getMessage());
            }
        }

        d.setResult(closedOrders > 0 ? "SUCCESS" : "FAILED");
        d.setConfirmBy(operator);
        d.setMessage(d.getMessage() + String.format(" | 已确认，发出%d笔平仓指令", closedOrders));
        d.setUpdateTime(LocalDateTime.now());
        dispositionLogMapper.updateById(d);

        if ("SUCCESS".equals(d.getResult())) {
            openBlockRegistry.release(d.getScopeType(), d.getScopeId());
        }
        return d;
    }

    /** 解除限制开仓（风险已释放或误触发） */
    public void releaseBlock(String scopeType, String scopeId) {
        openBlockRegistry.release(scopeType, scopeId);
    }

    public OpenBlockRegistry getOpenBlockRegistry() {
        return openBlockRegistry;
    }

    private boolean matchesAccountScope(IntradayRiskRule rule, String accountId) {
        return rule.getAccountId() == null || rule.getAccountId().isEmpty()
                || rule.getAccountId().equals(accountId);
    }

    private boolean matchSymbol(IntradayRiskRule rule, String symbol) {
        return rule.getSymbol() == null || rule.getSymbol().isEmpty()
                || rule.getSymbol().equals(symbol);
    }

    private BigDecimal positionValue(PositionDetail p) {
        return p.getAvgCost().multiply(p.getTotalVolume());
    }

    private JSONObject parseParams(String json) {
        if (json == null || json.isEmpty()) return new JSONObject();
        try {
            return JSON.parseObject(json);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private double pct(BigDecimal ratio) {
        return ratio.multiply(new BigDecimal("100")).doubleValue();
    }

    private List<PositionDetail> safeQueryPositions() {
        try {
            if (!tradeExecutionService.isAuthenticated()) return List.of();
            return tradeExecutionService.queryPositions();
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<FundAccount> safeQueryAccounts() {
        try {
            return fundAccountMapper.selectList(null);
        } catch (Exception e) {
            return List.of();
        }
    }
}
