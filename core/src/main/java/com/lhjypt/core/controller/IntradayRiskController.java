package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.mapper.RiskDispositionLogMapper;
import com.lhjypt.core.model.risk.IntradayRiskRule;
import com.lhjypt.core.model.risk.RiskDispositionLog;
import com.lhjypt.core.service.risk.intraday.IntradayRiskService;
import com.lhjypt.core.mapper.IntradayRiskRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 事中风控 API（P1）
 */
@RestController
@RequestMapping("/api/risk/intraday")
@RequiredArgsConstructor
public class IntradayRiskController {

    private final IntradayRiskService intradayRiskService;
    private final IntradayRiskRuleMapper ruleMapper;
    private final RiskDispositionLogMapper dispositionLogMapper;

    /**
     * 创建/更新事中规则
     */
    @PostMapping("/rule")
    public Result<IntradayRiskRule> saveRule(@RequestBody IntradayRiskRule rule) {
        if (rule.getRuleId() == null || rule.getRuleId().isEmpty()) {
            return Result.error("ruleId 不能为空");
        }
        IntradayRiskRule existing = ruleMapper.selectByRuleId(rule.getRuleId());
        if (existing != null) {
            rule.setId(existing.getId());
            rule.setCreateTime(existing.getCreateTime());
            rule.setUpdateTime(LocalDateTime.now());
            ruleMapper.updateById(rule);
        } else {
            if (rule.getEnabled() == null) rule.setEnabled(true);
            if (rule.getActionLevel() == null) rule.setActionLevel("WARN");
            if (rule.getCooldownSec() == null) rule.setCooldownSec(60);
            ruleMapper.insert(rule);
        }
        return Result.success(rule);
    }

    /**
     * 事中规则列表（含禁用）
     */
    @GetMapping("/rule/list")
    public Result<List<IntradayRiskRule>> listRules() {
        return Result.success(ruleMapper.selectList(null));
    }

    /**
     * 启用/停用规则
     */
    @PostMapping("/rule/{ruleId}/toggle")
    public Result<String> toggleRule(@PathVariable String ruleId,
                                     @RequestParam(defaultValue = "true") boolean enabled) {
        IntradayRiskRule rule = ruleMapper.selectByRuleId(ruleId);
        if (rule == null) {
            return Result.error("规则不存在: " + ruleId);
        }
        rule.setEnabled(enabled);
        rule.setUpdateTime(LocalDateTime.now());
        ruleMapper.updateById(rule);
        return Result.success("规则已" + (enabled ? "启用" : "停用") + ": " + ruleId);
    }

    /**
     * 账户实时风控状态
     */
    @GetMapping("/status/{accountId}")
    public Result<Map<String, Object>> accountStatus(@PathVariable String accountId) {
        return Result.success(intradayRiskService.accountRiskStatus(accountId));
    }

    /**
     * 手动触发一次全量评估（调试用）
     */
    @PostMapping("/evaluate")
    public Result<List<RiskDispositionLog>> evaluateNow() {
        return Result.success(intradayRiskService.evaluateAll());
    }

    /**
     * 确认执行强制平仓处置（敏感操作，需一次性确认令牌）
     */
    @PostMapping("/disposition/{dispositionId}/confirm")
    public Result<RiskDispositionLog> confirmDisposition(@PathVariable String dispositionId,
                                                          @RequestParam(defaultValue = "system") String operator,
                                                          @RequestHeader(value = "X-Confirm-Token", required = false) String confirmToken) {
        try {
            return Result.success(intradayRiskService.confirmForceClose(dispositionId, operator, confirmToken));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.error(409, e.getMessage());
        }
    }

    /**
     * 解除限制开仓（敏感操作）
     */
    @PostMapping("/block/release")
    public Result<String> releaseBlock(@RequestParam String scopeType,
                                       @RequestParam String scopeId) {
        intradayRiskService.releaseBlock(scopeType, scopeId);
        return Result.success("限制开仓已解除: " + scopeType + ":" + scopeId);
    }

    /**
     * 当前生效的限制开仓清单
     */
    @GetMapping("/block/list")
    public Result<Set<String>> listBlocks() {
        return Result.success(intradayRiskService.getOpenBlockRegistry().activeBlocks());
    }

    /**
     * 处置记录列表
     */
    @GetMapping("/disposition/list")
    public Result<List<RiskDispositionLog>> listDispositions() {
        return Result.success(dispositionLogMapper.selectRecent());
    }
}