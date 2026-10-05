package com.lhjypt.core.service.trade.gateway;

import com.lhjypt.core.mapper.GatewayRouteRuleMapper;
import com.lhjypt.core.model.gateway.GatewayRouteRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 路由规则持久化和 30 秒缓存。
 */
@Service
public class GatewayRouteService {

    private static final long CACHE_TTL_NANOS = 30_000_000_000L;

    private final GatewayRouteRuleMapper routeRuleMapper;

    private volatile List<GatewayRouteRule> cachedRules = List.of();
    private volatile long cacheExpiresAt;

    public GatewayRouteService(GatewayRouteRuleMapper routeRuleMapper) {
        this.routeRuleMapper = routeRuleMapper;
    }

    public List<GatewayRouteRule> enabledRules() {
        long now = System.nanoTime();
        if (now >= cacheExpiresAt) {
            refresh();
        }
        return cachedRules;
    }

    public List<GatewayRouteRule> list() {
        return routeRuleMapper.selectList(null);
    }

    @Transactional
    public GatewayRouteRule upsert(GatewayRouteRule rule) {
        if (rule.getRuleId() == null || rule.getRuleId().isBlank()) {
            rule.setRuleId("GR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (rule.getPriority() == null) {
            rule.setPriority(100);
        }
        if (rule.getEnabled() == null) {
            rule.setEnabled(true);
        }

        GatewayRouteRule existing = routeRuleMapper.selectByRuleId(rule.getRuleId());
        if (existing == null) {
            rule.setCreateTime(LocalDateTime.now());
            rule.setUpdateTime(LocalDateTime.now());
            routeRuleMapper.insert(rule);
        } else {
            rule.setId(existing.getId());
            rule.setCreateTime(existing.getCreateTime());
            rule.setUpdateTime(LocalDateTime.now());
            routeRuleMapper.updateById(rule);
        }
        refresh();
        return rule;
    }

    @Transactional
    public GatewayRouteRule switchAccount(String accountId, String gatewayId,
                                          String fallbackId, String operator) {
        GatewayRouteRule rule = new GatewayRouteRule();
        rule.setRuleId("MANUAL-" + accountId);
        rule.setRuleName("手动切换账户流量");
        rule.setAccountId(accountId);
        rule.setGatewayId(gatewayId);
        rule.setFallbackId(fallbackId);
        rule.setPriority(1);
        rule.setEnabled(true);
        return upsert(rule);
    }

    public synchronized void refresh() {
        cachedRules = List.copyOf(routeRuleMapper.selectEnabledRules());
        cacheExpiresAt = System.nanoTime() + CACHE_TTL_NANOS;
    }
}
