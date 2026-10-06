package com.lhjypt.core.service.risk;

import com.lhjypt.core.config.DistributedLock;
import com.lhjypt.core.mapper.RiskBudgetMapper;
import com.lhjypt.core.mapper.VarCalculationMapper;
import com.lhjypt.core.model.risk.RiskBudget;
import com.lhjypt.core.model.var.VarCalculation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskBudgetServiceTest {

    private RiskBudgetMapper budgetMapper;
    private VarCalculationMapper varMapper;
    private OpenBlockRegistry openBlockRegistry;
    private RiskBudgetService service;

    @BeforeEach
    void setUp() {
        budgetMapper = mock(RiskBudgetMapper.class);
        varMapper = mock(VarCalculationMapper.class);
        openBlockRegistry = new OpenBlockRegistry();
        service = new RiskBudgetService(budgetMapper, varMapper, openBlockRegistry,
                mock(DistributedLock.class));
    }

    @Test
    @DisplayName("新增预算：默认启用、状态 OK、生成预算号")
    void saveInsertsNewBudget() {
        when(budgetMapper.selectByScope("ACCOUNT", "ACC-1", "VAR")).thenReturn(null);
        RiskBudget budget = new RiskBudget();
        budget.setScopeType("ACCOUNT");
        budget.setScopeId("ACC-1");
        budget.setLimitValue(new BigDecimal("100"));

        RiskBudget saved = service.save(budget, "risk-1");

        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(RiskBudget.STATUS_OK);
        assertThat(saved.getBudgetId()).startsWith("BUD-");
        ArgumentCaptor<RiskBudget> captor = ArgumentCaptor.forClass(RiskBudget.class);
        verify(budgetMapper).insert(captor.capture());
        assertThat(captor.getValue().getUpdateTime()).isNotNull();
    }

    @Test
    @DisplayName("刷新：占用 ≥100% → BLOCK 并登记限制开仓")
    void refreshMarksBlockAndRegistersOpenBlock() {
        RiskBudget budget = enabledBudget("ACCOUNT", "ACC-1", "100");
        when(budgetMapper.selectEnabled()).thenReturn(List.of(budget));
        VarCalculation var = new VarCalculation();
        var.setVarValue(new BigDecimal("200"));
        when(varMapper.selectLatest("ACCOUNT", "ACC-1")).thenReturn(var);

        int refreshed = service.refreshAll();

        assertThat(refreshed).isEqualTo(1);
        assertThat(budget.getStatus()).isEqualTo(RiskBudget.STATUS_BLOCK);
        assertThat(budget.getUsageRate().compareTo(new BigDecimal("2"))).isEqualTo(0);
        assertThat(openBlockRegistry.isBlocked("ACC-1", null, null)).isTrue();
        verify(budgetMapper).updateById(budget);
    }

    @Test
    @DisplayName("刷新：占用 80%~100% → WARN 且不限制开仓")
    void refreshWarnDoesNotBlock() {
        RiskBudget budget = enabledBudget("ACCOUNT", "ACC-1", "100");
        when(budgetMapper.selectEnabled()).thenReturn(List.of(budget));
        VarCalculation var = new VarCalculation();
        var.setVarValue(new BigDecimal("90"));
        when(varMapper.selectLatest("ACCOUNT", "ACC-1")).thenReturn(var);

        service.refreshAll();

        assertThat(budget.getStatus()).isEqualTo(RiskBudget.STATUS_WARN);
        assertThat(openBlockRegistry.isBlocked("ACC-1", null, null)).isFalse();
    }

    @Test
    @DisplayName("刷新：占用回落 → BLOCK 解除")
    void refreshRecoversAndReleasesBlock() {
        RiskBudget budget = enabledBudget("ACCOUNT", "ACC-1", "100");
        when(budgetMapper.selectEnabled()).thenReturn(List.of(budget));
        VarCalculation high = new VarCalculation();
        high.setVarValue(new BigDecimal("200"));
        when(varMapper.selectLatest("ACCOUNT", "ACC-1")).thenReturn(high, null);

        service.refreshAll();
        assertThat(openBlockRegistry.isBlocked("ACC-1", null, null)).isTrue();

        when(varMapper.selectLatest("ACCOUNT", "ACC-1")).thenReturn(null);
        service.refreshAll();

        assertThat(budget.getStatus()).isEqualTo(RiskBudget.STATUS_OK);
        assertThat(openBlockRegistry.isBlocked("ACC-1", null, null)).isFalse();
    }

    @Test
    @DisplayName("无 VaR 数据：占用 0、状态 OK")
    void refreshNoDataIsOk() {
        RiskBudget budget = enabledBudget("STRATEGY", "S-1", "100");
        when(budgetMapper.selectEnabled()).thenReturn(List.of(budget));

        service.refreshAll();

        assertThat(budget.getCurrentValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(budget.getStatus()).isEqualTo(RiskBudget.STATUS_OK);
        verify(varMapper).selectLatest("STRATEGY", "S-1");
    }

    @Test
    @DisplayName("禁用预算不参与刷新")
    void disabledBudgetSkipped() {
        RiskBudget budget = enabledBudget("ACCOUNT", "ACC-1", "100");
        budget.setEnabled(false);
        when(budgetMapper.selectEnabled()).thenReturn(List.of());

        service.refreshAll();

        verify(varMapper, never()).selectLatest(any(), any());
    }

    private static RiskBudget enabledBudget(String scopeType, String scopeId, String limit) {
        RiskBudget budget = new RiskBudget();
        budget.setBudgetId("BUD-1");
        budget.setScopeType(scopeType);
        budget.setScopeId(scopeId);
        budget.setBudgetType(RiskBudget.TYPE_VAR);
        budget.setLimitValue(new BigDecimal(limit));
        budget.setEnabled(true);
        budget.setStatus(RiskBudget.STATUS_OK);
        return budget;
    }
}