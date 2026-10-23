package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.model.risk.RiskCheckResult;
import com.lhjypt.core.model.risk.RiskInterceptLog;
import com.lhjypt.core.model.trade.TradeRequest;
import com.lhjypt.core.service.risk.PreTradeRiskService;
import com.lhjypt.core.service.risk.RiskRule;
import com.lhjypt.core.service.risk.RiskRuleConfigService;
import com.lhjypt.core.service.risk.impl.BlacklistRule;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 事前风控API
 */
@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskController {

    private final PreTradeRiskService riskService;
    private final RiskRuleConfigService ruleConfigService;

    /**
     * 事前风控规则列表（含运行时开关状态）。
     */
    @GetMapping("/rules")
    public Result<List<RiskRuleConfigService.RuleView>> rules() {
        return Result.success(ruleConfigService.list());
    }

    /**
     * 启用/停用某条事前风控规则，立即生效。
     */
    @PostMapping("/rules/{ruleCode}/toggle")
    public Result<RiskRuleConfigService.RuleView> toggleRule(@PathVariable String ruleCode,
                                                             @RequestParam boolean enabled) {
        return Result.success(ruleConfigService.toggle(ruleCode, enabled));
    }

    /**
     * 风控检查（下单前调用）
     */
    @PostMapping("/check")
    public Result<RiskCheckResult> check(@RequestBody TradeRequest request) {
        // 使用简化的风控上下文（实际由交易服务注入真实数据）
        RiskRule.RiskContext context = new DefaultRiskContext();
        return Result.success(riskService.check(request, context));
    }

    /**
     * 查询拦截日志
     */
    @GetMapping("/intercepts")
    public Result<List<RiskInterceptLog>> getIntercepts() {
        return Result.success(List.of());
    }

    /**
     * 添加黑名单
     */
    @PostMapping("/blacklist/{symbol}")
    public Result<Void> addBlacklist(@PathVariable String symbol) {
        riskService.getBlacklistRule().addToBlacklist(symbol);
        return Result.success();
    }

    /**
     * 移除黑名单
     */
    @DeleteMapping("/blacklist/{symbol}")
    public Result<Void> removeBlacklist(@PathVariable String symbol) {
        riskService.getBlacklistRule().removeFromBlacklist(symbol);
        return Result.success();
    }

    /**
     * 查询黑名单
     */
    @GetMapping("/blacklist")
    public Result<Set<String>> getBlacklist() {
        return Result.success(riskService.getBlacklistRule().getBlacklist());
    }

    /**
     * 默认风控上下文（简化实现，实际对接持仓/资金服务）
     */
    static class DefaultRiskContext implements RiskRule.RiskContext {
        @Override public BigDecimal getCurrentPosition(String s) { return BigDecimal.ZERO; }
        @Override public BigDecimal getAvailableCash() { return new BigDecimal("1000000"); }
        @Override public BigDecimal getTotalAsset() { return new BigDecimal("1000000"); }
        @Override public int getOrderCount(String s, int w) { return 0; }
        @Override public BigDecimal getBidPrice(String s) { return null; }
        @Override public BigDecimal getAskPrice(String s) { return null; }
        @Override public boolean isBlacklisted(String s) { return false; }
    }
}
