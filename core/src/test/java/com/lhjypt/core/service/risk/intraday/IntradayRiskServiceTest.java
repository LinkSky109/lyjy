package com.lhjypt.core.service.risk.intraday;

import com.lhjypt.core.mapper.FundAccountMapper;
import com.lhjypt.core.mapper.IntradayRiskRuleMapper;
import com.lhjypt.core.mapper.RiskDispositionLogMapper;
import com.lhjypt.core.model.risk.IntradayRiskRule;
import com.lhjypt.core.model.risk.RiskDispositionLog;
import com.lhjypt.core.model.trade.PositionDetail;
import com.lhjypt.core.model.trade.TradeRequest;
import com.lhjypt.core.service.risk.OpenBlockRegistry;
import com.lhjypt.core.service.risk.PreTradeRiskService;
import com.lhjypt.core.service.trade.TradeExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * IntradayRiskService 评估与处置行为测试（P1）
 * mock 只喂运行时数据（持仓/规则/频率），断言落在真实处置结果与真实 OpenBlockRegistry 联动状态上。
 */
class IntradayRiskServiceTest {

    private IntradayRiskRuleMapper ruleMapper;
    private RiskDispositionLogMapper dispositionLogMapper;
    private TradeExecutionService tradeExecutionService;
    private FundAccountMapper fundAccountMapper;
    private PreTradeRiskService preTradeRiskService;
    private OpenBlockRegistry openBlockRegistry;
    private com.lhjypt.core.common.auth.ConfirmTokenService confirmTokenService;
    private IntradayRiskService service;

    @BeforeEach
    void setUp() {
        ruleMapper = mock(IntradayRiskRuleMapper.class);
        dispositionLogMapper = mock(RiskDispositionLogMapper.class);
        tradeExecutionService = mock(TradeExecutionService.class);
        fundAccountMapper = mock(FundAccountMapper.class);
        preTradeRiskService = mock(PreTradeRiskService.class);
        openBlockRegistry = new OpenBlockRegistry(); // 真实实例，验证联动
        confirmTokenService = new com.lhjypt.core.common.auth.ConfirmTokenService(); // 真实实例
        service = new IntradayRiskService(ruleMapper, dispositionLogMapper,
                tradeExecutionService, fundAccountMapper, preTradeRiskService,
                openBlockRegistry, confirmTokenService);

        when(tradeExecutionService.isAuthenticated()).thenReturn(true);
        when(fundAccountMapper.selectList(any())).thenReturn(List.of());
    }

    private IntradayRiskRule rule(String id, String type, String level, String params, String symbol, String strategy) {
        IntradayRiskRule r = new IntradayRiskRule();
        r.setRuleId(id);
        r.setName(id);
        r.setRuleType(type);
        r.setEnabled(true);
        r.setActionLevel(level);
        r.setThresholdParams(params);
        r.setSymbol(symbol);
        r.setStrategyId(strategy);
        r.setCooldownSec(60);
        return r;
    }

    private PositionDetail position(String symbol, String volume, String cost) {
        PositionDetail p = new PositionDetail();
        p.setSymbol(symbol);
        p.setExchange("SHFE");
        p.setDirection("LONG");
        p.setTotalVolume(new BigDecimal(volume));
        p.setAvgCost(new BigDecimal(cost));
        p.setAvailableVolume(new BigDecimal(volume));
        return p;
    }

    @Test
    @DisplayName("持仓超过绝对限额时生成限制开仓处置并联动登记")
    void positionOverVolumeLimitTriggersBlock() {
        when(ruleMapper.selectEnabledRules())
                .thenReturn(List.of(rule("PL1", "POSITION_LIMIT", "BLOCK", "{\"maxVolume\":50}", "rb2410", null)));
        when(tradeExecutionService.queryPositions())
                .thenReturn(List.of(position("rb2410", "100", "3500")));

        List<RiskDispositionLog> result = service.evaluateAll();

        assertThat(result).hasSize(1);
        RiskDispositionLog d = result.get(0);
        assertThat(d.getAction()).isEqualTo("BLOCK_OPEN");
        assertThat(d.getScopeType()).isEqualTo("SYMBOL");
        assertThat(d.getScopeId()).isEqualTo("rb2410");
        assertThat(d.getResult()).isEqualTo("SUCCESS");
        // 联动：事前风控读取该登记器时应视为受限
        assertThat(openBlockRegistry.isBlocked(null, null, "rb2410")).isTrue();
        verify(dispositionLogMapper).insert(any(RiskDispositionLog.class));
    }

    @Test
    @DisplayName("持仓未超限时不产生处置、不登记限制")
    void positionWithinLimitNoDisposition() {
        when(ruleMapper.selectEnabledRules())
                .thenReturn(List.of(rule("PL2", "POSITION_LIMIT", "BLOCK", "{\"maxVolume\":50}", "rb2410", null)));
        when(tradeExecutionService.queryPositions())
                .thenReturn(List.of(position("rb2410", "10", "3500")));

        List<RiskDispositionLog> result = service.evaluateAll();

        assertThat(result).isEmpty();
        assertThat(openBlockRegistry.isBlocked(null, null, "rb2410")).isFalse();
        verify(dispositionLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("强制平仓级别处置保持待确认，且不会自动登记限制开仓")
    void forceCloseStaysPending() {
        when(ruleMapper.selectEnabledRules())
                .thenReturn(List.of(rule("FC1", "POSITION_LIMIT", "FORCE_CLOSE", "{\"maxVolume\":50}", "rb2410", null)));
        when(tradeExecutionService.queryPositions())
                .thenReturn(List.of(position("rb2410", "100", "3500")));

        List<RiskDispositionLog> result = service.evaluateAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAction()).isEqualTo("FORCE_CLOSE");
        assertThat(result.get(0).getResult()).isEqualTo("PENDING");
        assertThat(openBlockRegistry.isBlocked(null, null, "rb2410")).isFalse();
        // 未经确认不发出平仓指令
        verify(tradeExecutionService, never()).placeOrder(any());
    }

    @Test
    @DisplayName("策略每分钟下单超限触发限制开仓")
    void orderRateOverLimitTriggersBlockOnStrategy() {
        when(ruleMapper.selectEnabledRules())
                .thenReturn(List.of(rule("OR1", "ORDER_RATE", "BLOCK", "{\"maxOrdersPerMinute\":5}", null, "S1")));
        when(tradeExecutionService.queryPositions()).thenReturn(List.of());
        when(preTradeRiskService.countOrders(eq("S1"), anyInt())).thenReturn(10);

        List<RiskDispositionLog> result = service.evaluateAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getScopeType()).isEqualTo("STRATEGY");
        assertThat(result.get(0).getScopeId()).isEqualTo("S1");
        assertThat(openBlockRegistry.isBlocked(null, "S1", null)).isTrue();
    }

    @Test
    @DisplayName("同一规则同一作用域在冷却窗口内只处置一次")
    void cooldownSuppressesDuplicateDisposition() {
        when(ruleMapper.selectEnabledRules())
                .thenReturn(List.of(rule("PL3", "POSITION_LIMIT", "BLOCK", "{\"maxVolume\":50}", "rb2410", null)));
        when(tradeExecutionService.queryPositions())
                .thenReturn(List.of(position("rb2410", "100", "3500")));

        service.evaluateAll();
        List<RiskDispositionLog> second = service.evaluateAll();

        assertThat(second).isEmpty();
        verify(dispositionLogMapper, times(1)).insert(any());
    }

    private RiskDispositionLog pendingForceClose() {
        RiskDispositionLog pending = new RiskDispositionLog();
        pending.setDispositionId("DSP-X1");
        pending.setAction("FORCE_CLOSE");
        pending.setResult("PENDING");
        pending.setScopeType("SYMBOL");
        pending.setScopeId("rb2410");
        pending.setMessage("持仓超限");
        when(dispositionLogMapper.selectByDispositionId("DSP-X1")).thenReturn(pending);
        return pending;
    }

    @Test
    @DisplayName("强制平仓缺少二次确认令牌时拒绝执行且不发出平仓指令")
    void forceCloseRejectedWithoutConfirmToken() {
        pendingForceClose();
        when(tradeExecutionService.queryPositions())
                .thenReturn(List.of(position("rb2410", "100", "3500")));

        assertThatThrownBy(() -> service.confirmForceClose("DSP-X1", "admin", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("二次确认");
        assertThatThrownBy(() -> service.confirmForceClose("DSP-X1", "admin", "forged-token"))
                .isInstanceOf(IllegalStateException.class);

        verify(tradeExecutionService, never()).placeOrder(any());
    }

    @Test
    @DisplayName("携带有效确认令牌后强制平仓执行并发出平仓指令")
    void forceCloseExecutesWithValidConfirmToken() {
        pendingForceClose();
        when(tradeExecutionService.queryPositions())
                .thenReturn(List.of(position("rb2410", "100", "3500")));
        String token = confirmTokenService.issue("RISK_OFFICER");

        RiskDispositionLog done = service.confirmForceClose("DSP-X1", "risk-boss", token);

        assertThat(done.getResult()).isEqualTo("SUCCESS");
        assertThat(done.getConfirmBy()).isEqualTo("risk-boss");
        verify(tradeExecutionService).placeOrder(any(TradeRequest.class));
        // 令牌一次性：重放同一令牌仍被拒绝
        assertThatThrownBy(() -> service.confirmForceClose("DSP-X1", "risk-boss", token))
                .isInstanceOf(IllegalStateException.class);
    }
}
