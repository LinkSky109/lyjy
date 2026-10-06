package com.lhjypt.core.service.risk;

import com.lhjypt.core.config.DistributedLock;
import com.lhjypt.core.mapper.RiskBudgetMapper;
import com.lhjypt.core.mapper.VarCalculationMapper;
import com.lhjypt.core.model.risk.RiskBudget;
import com.lhjypt.core.model.var.VarCalculation;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 风险预算分配与占用监控（P2 模块二 §2.3）。
 *
 * <p>预算以 VaR 口径度量：{@code risk_budget}(scope, type=VAR, limit)。
 * 定时/手动刷新：读取最近一次 VaR 计算作为当前占用，占用率 = current/limit，
 * {@code ≥100% → BLOCK}（联动 {@link OpenBlockRegistry} 限制开仓）、
 * {@code ≥80% → WARN}；回落即解禁。策略/账户/组合三种作用域统一处理。
 */
@Service
@RequiredArgsConstructor
public class RiskBudgetService {

    private static final Logger log = LoggerFactory.getLogger(RiskBudgetService.class);

    private static final BigDecimal WARN_RATE = new BigDecimal("0.8");
    private static final BigDecimal BLOCK_RATE = BigDecimal.ONE;
    private static final int BLOCK_DURATION_SECONDS = 300;

    private final RiskBudgetMapper budgetMapper;
    private final VarCalculationMapper varCalculationMapper;
    private final OpenBlockRegistry openBlockRegistry;
    private final DistributedLock distributedLock;

    @Scheduled(fixedRateString = "${lhjypt.risk.budget.refresh-interval-ms:30000}")
    public void scheduledRefresh() {
        distributedLock.execute("risk.budget.refresh", this::refreshAll);
    }

    @Transactional
    public RiskBudget save(RiskBudget budget, String operator) {
        if (budget == null || budget.getScopeType() == null || budget.getScopeId() == null
                || budget.getLimitValue() == null) {
            throw new IllegalArgumentException("scopeType/scopeId/limitValue 不能为空");
        }
        String scopeType = budget.getScopeType().trim().toUpperCase();
        String budgetType = budget.getBudgetType() == null
                ? RiskBudget.TYPE_VAR : budget.getBudgetType().trim().toUpperCase();
        if (!RiskBudget.TYPE_VAR.equals(budgetType)) {
            throw new IllegalArgumentException("budgetType 当前仅支持 VAR");
        }

        RiskBudget existing = budgetMapper.selectByScope(scopeType, budget.getScopeId(), budgetType);
        RiskBudget target = existing == null ? new RiskBudget() : existing;
        target.setBudgetId(existing == null
                ? "BUD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
                : existing.getBudgetId());
        target.setScopeType(scopeType);
        target.setScopeId(budget.getScopeId());
        target.setBudgetType(budgetType);
        target.setLimitValue(budget.getLimitValue());
        target.setRemark(budget.getRemark());
        target.setEnabled(budget.getEnabled() == null ? Boolean.TRUE : budget.getEnabled());
        target.setUpdatedBy(operator);
        target.setUpdateTime(LocalDateTime.now());
        if (existing == null) {
            target.setStatus(RiskBudget.STATUS_OK);
            target.setCreateTime(LocalDateTime.now());
            budgetMapper.insert(target);
        } else {
            budgetMapper.updateById(target);
        }
        return target;
    }

    public List<RiskBudget> list(String scopeType, String scopeId) {
        return budgetMapper.selectFiltered(blankToNull(scopeType), blankToNull(scopeId));
    }

    public RiskBudget detail(String budgetId) {
        RiskBudget budget = budgetMapper.selectByBudgetId(budgetId);
        if (budget == null) {
            throw new IllegalArgumentException("风险预算不存在: " + budgetId);
        }
        return budget;
    }

    /** 全量刷新占用（定时 + 手动）。 */
    @Transactional
    public int refreshAll() {
        int refreshed = 0;
        for (RiskBudget budget : budgetMapper.selectEnabled()) {
            refreshCurrent(budget, true);
            refreshed++;
        }
        log.info("风险预算占用已刷新: {} 条", refreshed);
        return refreshed;
    }

    /** 刷新单个预算（含 WARN/BLOCK 状态与开仓限制联动）。 */
    @Transactional
    public RiskBudget refreshOne(String budgetId) {
        RiskBudget budget = detail(budgetId);
        refreshCurrent(budget, true);
        return budget;
    }

    private void refreshCurrent(RiskBudget budget, boolean persist) {
        VarCalculation latest = varCalculationMapper.selectLatest(
                budget.getScopeType(), budget.getScopeId());
        BigDecimal current = latest == null || latest.getVarValue() == null
                ? BigDecimal.ZERO : latest.getVarValue().setScale(8, RoundingMode.HALF_UP);
        BigDecimal rate = budget.getLimitValue().signum() > 0
                ? current.divide(budget.getLimitValue(), 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        budget.setCurrentValue(current);
        budget.setUsageRate(rate);
        budget.setStatus(rate.compareTo(BLOCK_RATE) >= 0
                ? RiskBudget.STATUS_BLOCK
                : rate.compareTo(WARN_RATE) >= 0 ? RiskBudget.STATUS_WARN : RiskBudget.STATUS_OK);
        budget.setUpdateTime(LocalDateTime.now());

        if (persist) {
            budgetMapper.updateById(budget);
        }

        if (RiskBudget.STATUS_BLOCK.equals(budget.getStatus())) {
            openBlockRegistry.block(budget.getScopeType(), budget.getScopeId(),
                    BLOCK_DURATION_SECONDS);
            log.warn("风险预算超限[限制开仓] {}:{} usage={}", budget.getScopeType(),
                    budget.getScopeId(), budget.getUsageRate());
        } else {
            openBlockRegistry.release(budget.getScopeType(), budget.getScopeId());
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}