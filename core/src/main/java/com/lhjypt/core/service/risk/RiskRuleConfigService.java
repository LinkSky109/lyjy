package com.lhjypt.core.service.risk;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lhjypt.core.mapper.RiskRuleConfigMapper;
import com.lhjypt.core.model.risk.RiskRuleConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(RiskRuleConfigService.class);

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
        RiskRule rule = requireRule(ruleCode);
        RiskRuleConfig config = findConfig(rule.getCode());
        if (config == null) {
            config = newConfig(rule, enabled);
            mapper.insert(config);
        } else {
            config.setEnabled(enabled);
            config.setUpdateTime(LocalDateTime.now());
            mapper.updateById(config);
        }
        overrides.put(rule.getCode(), enabled);
        return toView(rule, config);
    }

    /**
     * 更新规则阈值与动作。阈值先应用到规则实例，再落库；落库失败回滚运行时参数。
     */
    public RuleView update(String ruleCode, String thresholdParams, String action,
                           String operator) {
        RiskRule rule = requireRule(ruleCode);
        boolean hasThresholds = thresholdParams != null && !thresholdParams.isBlank();
        if (hasThresholds && !(rule instanceof DynamicRiskRule)) {
            throw new IllegalArgumentException("该规则不支持阈值编辑: " + rule.getCode());
        }
        String normalizedAction = action == null || action.isBlank()
                ? null : action.trim().toUpperCase();
        if (normalizedAction != null
                && !normalizedAction.equals("REJECT") && !normalizedAction.equals("WARN")) {
            throw new IllegalArgumentException("action 仅支持 REJECT/WARN");
        }

        RiskRuleConfig config = findConfig(rule.getCode());
        if (config == null) {
            config = newConfig(rule, rule.isEnabled());
        }
        String previousParams = config.getThresholdParams();
        String previousAction = config.getAction();

        if (hasThresholds) {
            ((DynamicRiskRule) rule).applyThresholds(thresholdParams);
            config.setThresholdParams(thresholdParams.trim());
        }
        if (normalizedAction != null) {
            config.setAction(normalizedAction);
        }
        config.setUpdateTime(LocalDateTime.now());
        try {
            if (config.getId() == null) {
                mapper.insert(config);
            } else {
                mapper.updateById(config);
            }
        } catch (RuntimeException error) {
            rollback(rule, previousParams);
            config.setThresholdParams(previousParams);
            config.setAction(previousAction);
            throw error;
        }
        log.info("风控规则更新: rule={} operator={} params={} action={}",
                rule.getCode(), operator, config.getThresholdParams(), config.getAction());
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
            if (config.getThresholdParams() == null || config.getThresholdParams().isBlank()) {
                return;
            }
            rules.stream()
                    .filter(rule -> rule.getCode().equalsIgnoreCase(code))
                    .filter(DynamicRiskRule.class::isInstance)
                    .findFirst()
                    .ifPresent(rule -> {
                        try {
                            ((DynamicRiskRule) rule).applyThresholds(config.getThresholdParams());
                        } catch (RuntimeException error) {
                            log.warn("风控规则阈值加载失败: rule={} params={} error={}",
                                    code, config.getThresholdParams(), error.getMessage());
                        }
                    });
        });
    }

    private RiskRule requireRule(String ruleCode) {
        return rules.stream()
                .filter(item -> item.getCode().equalsIgnoreCase(ruleCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知风控规则: " + ruleCode));
    }

    private RiskRuleConfig findConfig(String ruleCode) {
        return mapper.selectOne(new QueryWrapper<RiskRuleConfig>().eq("rule_code", ruleCode));
    }

    private static RiskRuleConfig newConfig(RiskRule rule, boolean enabled) {
        RiskRuleConfig config = new RiskRuleConfig();
        config.setRuleCode(rule.getCode());
        config.setRuleName(rule.getName());
        config.setRuleType("PRETRADE");
        config.setAction("REJECT");
        config.setEnabled(enabled);
        config.setCreateTime(LocalDateTime.now());
        config.setUpdateTime(LocalDateTime.now());
        return config;
    }

    private static void rollback(RiskRule rule, String previousParams) {
        if (!(rule instanceof DynamicRiskRule dynamic)) {
            return;
        }
        try {
            dynamic.applyThresholds(previousParams == null || previousParams.isBlank()
                    ? dynamic.getDefaultThresholds() : previousParams);
        } catch (RuntimeException error) {
            log.warn("风控规则阈值回滚失败: rule={} error={}", rule.getCode(), error.getMessage());
        }
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
        boolean editable = rule instanceof DynamicRiskRule;
        return new RuleView(
                rule.getCode(),
                rule.getName(),
                enabled,
                config != null,
                config == null ? null : config.getThresholdParams(),
                editable ? ((DynamicRiskRule) rule).getDefaultThresholds() : null,
                editable,
                config == null ? "REJECT" : config.getAction(),
                config == null ? null : config.getUpdateTime());
    }

    public record RuleView(
            String ruleCode,
            String ruleName,
            boolean enabled,
            boolean configured,
            String thresholdParams,
            String defaultThresholdParams,
            boolean editable,
            String action,
            LocalDateTime updatedAt) {
    }
}
