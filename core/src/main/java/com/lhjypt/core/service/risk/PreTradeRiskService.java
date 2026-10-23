package com.lhjypt.core.service.risk;

import com.lhjypt.core.model.risk.RiskCheckResult;
import com.lhjypt.core.model.risk.RiskInterceptLog;
import com.lhjypt.core.model.trade.*;
import com.lhjypt.core.service.risk.impl.*;
import com.lhjypt.core.mapper.RiskInterceptLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 事前风控服务
 * 下单前统一执行所有风控规则，任一REJECT规则触发则拦截订单
 *
 * 执行流程：
 * 1. 构建风控上下文（持仓、资金、频率统计）
 * 2. 依次执行所有启用的风控规则
 * 3. 汇总检查结果
 * 4. 拦截则记录日志，通过则放行
 */
@Service
@RequiredArgsConstructor
public class PreTradeRiskService {
    private static final Logger log = LoggerFactory.getLogger(PreTradeRiskService.class);


    private final PositionLimitRule positionLimitRule;
    private final FundRiskRule fundRiskRule;
    private final OrderFrequencyRule orderFrequencyRule;
    private final SelfTradePreventionRule selfTradeRule;
    private final BlacklistRule blacklistRule;
    private final RiskRuleConfigService ruleConfigService;
    private final RiskInterceptLogMapper interceptLogMapper;
    private final OpenBlockRegistry openBlockRegistry;
    private final com.lhjypt.core.service.degrade.DegradeService degradeService;

    /** 所有风控规则 */
    private List<RiskRule> rules;

    /** 下单时间记录（策略ID → 时间戳列表），用于频率统计 */
    private final Map<String, List<Long>> orderTimestamps = new ConcurrentHashMap<>();

    /**
     * 执行事前风控检查
     */
    public RiskCheckResult check(TradeRequest request, RiskRule.RiskContext context) {
        RiskCheckResult result = RiskCheckResult.pass();

        // 过载保护：订单入口令牌桶限流（核心链路只限流不降级；平仓放行保障风险释放）
        if (isOpenOrder(request) && !degradeService.tryAcquireOrderPermit()) {
            result.addViolation("OVERLOAD_LIMIT", "过载保护限流",
                    "订单入口达到容量上限（500 TPS），新开仓请求被限流拒绝，请稍后重试",
                    "REJECT");
            saveInterceptLog(request, result.getViolations().get(result.getViolations().size() - 1));
            return result;
        }

        // 事中风控联动：被限制开仓的作用域禁止新开仓（平仓放行）
        if (isOpenOrder(request)
                && openBlockRegistry.isBlocked(request.getAccountId(), request.getStrategyId(), request.getSymbol())) {
            result.addViolation("INTRADAY_BLOCK_OPEN", "事中限制开仓",
                    String.format("账户/策略/标的[%s|%s|%s]已被事中风控限制开仓，仅允许平仓",
                            request.getAccountId(), request.getStrategyId(), request.getSymbol()),
                    "REJECT");
            saveInterceptLog(request, result.getViolations().get(result.getViolations().size() - 1));
            return result;
        }

        for (RiskRule rule : getRules()) {
            if (ruleConfigService.isEnabled(rule.getCode(), rule.isEnabled())) {
                try {
                    rule.check(request, context, result);
                } catch (Exception e) {
                    log.error("风控规则执行异常: {}", rule.getCode(), e);
                }
            }
        }

        // 记录下单时间（通过频率检查后）
        if (result.isPassed()) {
            recordOrder(request.getStrategyId());
        } else {
            // 记录拦截日志
            for (RiskCheckResult.RuleViolation v : result.getViolations()) {
                if ("REJECT".equals(v.action())) {
                    saveInterceptLog(request, v);
                }
            }
            log.warn("订单被风控拦截: {} 原因数={}", request.getSymbol(),
                    result.getViolations().size());
        }

        return result;
    }

    /**
     * 获取频率统计：时间窗口内下单次数
     */
    public int countOrders(String strategyId, int windowSeconds) {
        String key = strategyId == null ? "DEFAULT" : strategyId;
        List<Long> timestamps = orderTimestamps.get(key);
        if (timestamps == null) return 0;

        long cutoff = System.currentTimeMillis() - windowSeconds * 1000L;
        return (int) timestamps.stream().filter(t -> t > cutoff).count();
    }

    private void recordOrder(String strategyId) {
        String key = strategyId == null ? "DEFAULT" : strategyId;
        orderTimestamps.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>())
                .add(System.currentTimeMillis());
    }

    private void saveInterceptLog(TradeRequest request, RiskCheckResult.RuleViolation v) {
        RiskInterceptLog logEntry = new RiskInterceptLog();
        logEntry.setAccountId(request.getAccountId());
        logEntry.setStrategyId(request.getStrategyId());
        logEntry.setSymbol(request.getSymbol());
        logEntry.setRuleCode(v.ruleCode());
        logEntry.setRuleName(v.ruleName());
        logEntry.setReason(v.message());
        logEntry.setDirection(request.getDirection());
        logEntry.setPrice(request.getPrice());
        logEntry.setQuantity(request.getQuantity());
        logEntry.setCreateTime(LocalDateTime.now());
        interceptLogMapper.insert(logEntry);
    }

    /**
     * 是否新开仓指令（平仓类 offsetFlag 放行；开仓/未标注视为新开仓）
     */
    private boolean isOpenOrder(TradeRequest request) {
        String flag = request.getOffsetFlag();
        if (flag == null) return true;
        return "OPEN".equalsIgnoreCase(flag);
    }

    private List<RiskRule> getRules() {
        if (rules == null) {
            rules = List.of(positionLimitRule, fundRiskRule,
                    orderFrequencyRule, selfTradeRule, blacklistRule);
        }
        return rules;
    }

    public BlacklistRule getBlacklistRule() { return blacklistRule; }
}
