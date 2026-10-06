package com.lhjypt.core.service.regulatory;

import com.alibaba.fastjson2.JSON;
import com.lhjypt.core.mapper.AmlAlertMapper;
import com.lhjypt.core.mapper.AmlRuleMapper;
import com.lhjypt.core.mapper.TradeRecordMapper;
import com.lhjypt.core.model.regulatory.AmlAlert;
import com.lhjypt.core.model.regulatory.AmlRule;
import com.lhjypt.core.model.trade.TradeRecord;
import com.lhjypt.core.model.trade.TurnoverAggregate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AML 可疑/大额交易监测（P2 模块四）。
 *
 * <p>按启用规则扫描成交流：
 * <ul>
 *   <li>{@code LARGE_SINGLE}：单笔成交额 ≥ 阈值 → 告警（去重键：规则+账户+策略+标的）；</li>
 *   <li>{@code LARGE_CUMULATIVE}：账户在 period 窗口内累计成交额 ≥ 阈值 → 告警（去重键：规则+账户）。</li>
 * </ul>
 * 告警流程 OPEN → REPORTED(人工复核，确认令牌) → CLOSED。
 */
@Service
@RequiredArgsConstructor
public class AmlMonitoringService {

    private final AmlRuleMapper amlRuleMapper;
    private final AmlAlertMapper amlAlertMapper;
    private final TradeRecordMapper tradeRecordMapper;
    private final com.lhjypt.core.common.auth.ConfirmTokenService confirmTokenService;

    public List<AmlRule> rules() {
        return amlRuleMapper.selectEnabledRules();
    }

    public List<AmlRule> allRules() {
        return amlRuleMapper.selectList(null);
    }

    @Transactional
    public AmlRule saveRule(AmlRule rule) {
        if (rule == null || rule.getRuleId() == null || rule.getRuleId().isBlank()) {
            throw new IllegalArgumentException("ruleId 不能为空");
        }
        if (rule.getEnabled() == null) {
            rule.setEnabled(true);
        }
        if (rule.getSeverity() == null) {
            rule.setSeverity("WARN");
        }
        AmlRule existing = amlRuleMapper.selectByRuleId(rule.getRuleId());
        if (existing == null) {
            rule.setCreateTime(LocalDateTime.now());
            rule.setUpdateTime(LocalDateTime.now());
            amlRuleMapper.insert(rule);
        } else {
            rule.setId(existing.getId());
            rule.setUpdateTime(LocalDateTime.now());
            amlRuleMapper.updateById(rule);
        }
        return rule;
    }

    public List<AmlAlert> alerts(String status) {
        String normalized = (status == null || status.isBlank())
                ? null : status.trim().toUpperCase();
        return amlAlertMapper.selectByStatus(normalized);
    }

    public AmlAlert alert(String alertId) {
        AmlAlert alert = amlAlertMapper.selectByAlertId(alertId);
        if (alert == null) {
            throw new IllegalArgumentException("AML 告警不存在: " + alertId);
        }
        return alert;
    }

    /** 手动/定时扫描（定时扫描由上层 @Scheduled 驱动，本方法幂等去重）。 */
    @Transactional
    public List<AmlAlert> scan() {
        LocalDateTime now = LocalDateTime.now();
        List<AmlAlert> created = new ArrayList<>();
        for (AmlRule rule : amlRuleMapper.selectEnabledRules()) {
            if (AmlRule.LARGE_SINGLE.equals(rule.getRuleCode())) {
                List<TradeRecord> trades = tradeRecordMapper.selectLargeTrades(
                        rule.getThreshold(), now.minusDays(30));
                for (TradeRecord trade : trades) {
                    AmlAlert alert = createSingle(rule, trade);
                    if (alert != null) {
                        created.add(alert);
                    }
                }
            } else if (AmlRule.LARGE_CUMULATIVE.equals(rule.getRuleCode())) {
                int window = rule.getPeriodSeconds() == null || rule.getPeriodSeconds() <= 0
                        ? 86400 : rule.getPeriodSeconds();
                List<TurnoverAggregate> aggregates = tradeRecordMapper.selectCumulativeTurnover(
                        now.minusSeconds(window), rule.getThreshold());
                for (TurnoverAggregate aggregate : aggregates) {
                    AmlAlert alert = createCumulative(rule, aggregate);
                    if (alert != null) {
                        created.add(alert);
                    }
                }
            }
        }
        return created;
    }

    /** 人工复核并上报（敏感操作，需一次性确认令牌，留痕）。 */
    @Transactional
    public AmlAlert report(String alertId, String operator, String note, String confirmToken) {
        if (!confirmTokenService.validate(confirmToken)) {
            throw new IllegalStateException("上报 AML 告警需要有效的二次确认令牌");
        }
        AmlAlert alert = alert(alertId);
        if (!AmlAlert.OPEN.equals(alert.getStatus())) {
            throw new IllegalStateException("仅 OPEN 告警可上报，当前 " + alert.getStatus());
        }
        alert.setStatus(AmlAlert.REPORTED);
        alert.setHandler(operator);
        alert.setHandleNote(note);
        alert.setUpdateTime(LocalDateTime.now());
        amlAlertMapper.updateById(alert);
        return alert;
    }

    @Transactional
    public AmlAlert close(String alertId, String operator, String note) {
        AmlAlert alert = alert(alertId);
        if (AmlAlert.CLOSED.equals(alert.getStatus())) {
            return alert;
        }
        alert.setStatus(AmlAlert.CLOSED);
        alert.setHandler(operator);
        alert.setHandleNote(note);
        alert.setUpdateTime(LocalDateTime.now());
        amlAlertMapper.updateById(alert);
        return alert;
    }

    private AmlAlert createSingle(AmlRule rule, TradeRecord trade) {
        if (amlAlertMapper.countOpenDedupe(rule.getRuleCode(), trade.getAccountId(),
                trade.getStrategyId(), trade.getSymbol()) > 0) {
            return null;
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("tradeId", trade.getTradeId());
        evidence.put("orderId", trade.getOrderId());
        evidence.put("tradeTime", trade.getTradeTime());
        evidence.put("direction", trade.getDirection());
        AmlAlert alert = new AmlAlert();
        alert.setAlertId("AML-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        alert.setRuleCode(rule.getRuleCode());
        alert.setMode(AmlRule.LARGE_SINGLE);
        alert.setAccountId(trade.getAccountId());
        alert.setStrategyId(trade.getStrategyId());
        alert.setSymbol(trade.getSymbol());
        alert.setAmount(trade.getTurnover());
        alert.setThreshold(rule.getThreshold());
        alert.setEvidence(JSON.toJSONString(evidence));
        alert.setSeverity(rule.getSeverity());
        alert.setStatus(AmlAlert.OPEN);
        alert.setCreateTime(LocalDateTime.now());
        alert.setUpdateTime(LocalDateTime.now());
        amlAlertMapper.insert(alert);
        return alert;
    }

    private AmlAlert createCumulative(AmlRule rule, TurnoverAggregate aggregate) {
        if (amlAlertMapper.countOpenDedupe(rule.getRuleCode(), aggregate.getAccountId(),
                null, null) > 0) {
            return null;
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("mode", "LARGE_CUMULATIVE");
        evidence.put("windowSeconds", rule.getPeriodSeconds());
        AmlAlert alert = new AmlAlert();
        alert.setAlertId("AML-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        alert.setRuleCode(rule.getRuleCode());
        alert.setMode(AmlRule.LARGE_CUMULATIVE);
        alert.setAccountId(aggregate.getAccountId());
        alert.setAmount(aggregate.getTurnover());
        alert.setThreshold(rule.getThreshold());
        alert.setEvidence(JSON.toJSONString(evidence));
        alert.setSeverity(rule.getSeverity());
        alert.setStatus(AmlAlert.OPEN);
        alert.setCreateTime(LocalDateTime.now());
        alert.setUpdateTime(LocalDateTime.now());
        amlAlertMapper.insert(alert);
        return alert;
    }

    private static BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}