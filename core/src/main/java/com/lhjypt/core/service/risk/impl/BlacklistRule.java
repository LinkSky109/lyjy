package com.lhjypt.core.service.risk.impl;

import com.lhjypt.core.model.risk.RiskCheckResult;
import com.lhjypt.core.model.trade.TradeRequest;
import com.lhjypt.core.service.risk.RiskRule;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 黑白名单规则
 * - 黑名单标的禁止交易
 * - 白名单模式下仅允许名单内标的交易
 */
@Component
public class BlacklistRule implements RiskRule {

    private boolean enabled = true;

    /** 黑名单 */
    private final Set<String> blacklist = ConcurrentHashMap.newKeySet();

    /** 白名单（为空则不启用白名单模式） */
    private final Set<String> whitelist = ConcurrentHashMap.newKeySet();

    @Override
    public String getCode() { return "BLACKLIST"; }

    @Override
    public String getName() { return "黑白名单"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void check(TradeRequest request, RiskContext context, RiskCheckResult result) {
        if (!enabled) return;
        String symbol = request.getSymbol();

        if (context.isBlacklisted(symbol) || blacklist.contains(symbol)) {
            result.addViolation(getCode(), getName(),
                    String.format("标的 %s 在黑名单中，禁止交易", symbol),
                    "REJECT");
        }

        if (!whitelist.isEmpty() && !whitelist.contains(symbol)) {
            result.addViolation(getCode(), getName(),
                    String.format("白名单模式下标的 %s 不允许交易", symbol),
                    "REJECT");
        }
    }

    public void addToBlacklist(String symbol) { blacklist.add(symbol); }
    public void removeFromBlacklist(String symbol) { blacklist.remove(symbol); }
    public void addToWhitelist(String symbol) { whitelist.add(symbol); }
    public Set<String> getBlacklist() { return Set.copyOf(blacklist); }
}
