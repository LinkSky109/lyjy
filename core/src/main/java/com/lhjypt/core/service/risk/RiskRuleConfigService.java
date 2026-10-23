package com.lhjypt.core.service.risk;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lhjypt.core.mapper.RiskRuleConfigMapper;
import com.lhjypt.core.model.risk.RiskRuleConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事前风控规则的运行时开关。
 * 默认沿用规则自身 enabled；risk_rule_config 中的记录覆盖默认值，切换即时生效。
 * 阈值参数当前只读展示，动态阈值需各规则支持参数注入后再开放编辑。
 */
@Service
public class RiskRuleConfigService {

    private final RiskRuleConfigMapper mapper;
    private final List<RiskRule> rules;
    private final Map<String, Boolean> overrides = new ConcurrentHashMap<>();

    public RiskRuleConfigService(RiskRuleConfigMapper mapper, List<RiskRule> rules) {
        this.mapper = mapper;
        this.rules = rules;
    }

    @PostConstruct
    void load() {
        refreshOverrides();
    }

    public List<RuleView> list() {
        Map<String, RiskRuleConfig> configs = configMap();
        return rules.stream()
                .map(rule -> toView(rule, configs.get(rule.getCode())))
                .toList();
    }

    public RuleView toggle(String ruleCode, boolean enabled) {
        RiskRule rule = rules.stream()
                .filter(item -> item.getCode().equalsIgnoreCase(ruleCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知风控规则: " + ruleCode));
        RiskRuleConfig config = mapper.selectOne(
                new QueryWrapper<RiskRuleConfig>().eq("rule_code", rule.getCode()));
        if (config == null) {
            config = new RiskRuleConfig();
            config.setRuleCode(rule.getCode());
            config.setRuleName(rule.getName());
            config.setRuleType("PRETRADE");
            config.setAction("REJECT");
            config.setEnabled(enabled);
            config.setCreateTime(LocalDateTime.now());
            config.setUpdateTime(LocalDateTime.now());
            mapper.insert(config);
        } else {
            config.setEnabled(enabled);
            config.setUpdateTime(LocalDateTime.now());
            mapper.updateById(config);
        }
        overrides.put(rule.getCode(), enabled);
        return toView(rule, config);
    }

    public boolean isEnabled(String ruleCode, boolean fallback) {
        return overrides.getOrDefault(ruleCode, fallback);
    }

    private void refreshOverrides() {
        overrides.clear();
        configMap().forEach((code, config) -> {
            if (config.getEnabled() != null) {
                overrides.put(code, config.getEnabled());
            }
        });
    }

    private Map<String, RiskRuleConfig> configMap() {
        Map<String, RiskRuleConfig> result = new LinkedHashMap<>();
        mapper.selectList(null).forEach(config -> result.put(config.getRuleCode(), config));
        return result;
    }

    private static RuleView toView(RiskRule rule, RiskRuleConfig config) {
        boolean enabled = config != null && config.getEnabled() != null
                ? config.getEnabled()
                : rule.isEnabled();
        return new RuleView(
                rule.getCode(),
                rule.getName(),
                enabled,
                config != null,
                config == null ? null : config.getThresholdParams(),
                config == null ? "REJECT" : config.getAction(),
                config == null ? null : config.getUpdateTime());
    }

    public record RuleView(
            String ruleCode,
            String ruleName,
            boolean enabled,
            boolean configured,
            String thresholdParams,
            String action,
            LocalDateTime updatedAt) {
    }
}
