package com.lhjypt.core.service.risk;

import com.lhjypt.core.mapper.RiskInterceptLogMapper;
import com.lhjypt.core.model.risk.RiskCheckResult;
import com.lhjypt.core.model.trade.TradeRequest;
import com.lhjypt.core.service.degrade.DegradeService;
import com.lhjypt.core.service.risk.RiskRuleConfigService;
import com.lhjypt.core.service.risk.impl.BlacklistRule;
import com.lhjypt.core.service.risk.impl.FundRiskRule;
import com.lhjypt.core.service.risk.impl.OrderFrequencyRule;
import com.lhjypt.core.service.risk.impl.PositionLimitRule;
import com.lhjypt.core.service.risk.impl.SelfTradePreventionRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 订单入口过载保护行为测试（TASK-P1-004 接入点）
 */
class PreTradeRiskServiceRateLimitTest {

    private DegradeService degradeService;
    private PreTradeRiskService service;

    @BeforeEach
    void setUp() {
        degradeService = mock(DegradeService.class);
        service = new PreTradeRiskService(
                mock(PositionLimitRule.class),
                mock(FundRiskRule.class),
                mock(OrderFrequencyRule.class),
                mock(SelfTradePreventionRule.class),
                mock(BlacklistRule.class),
                mock(RiskRuleConfigService.class),
                mock(RiskInterceptLogMapper.class),
                mock(OpenBlockRegistry.class),
                degradeService);
        when(degradeService.tryAcquireOrderPermit()).thenReturn(true);
    }

    private TradeRequest req() {
        TradeRequest r = new TradeRequest();
        r.setSymbol("rb2410");
        r.setDirection("BUY");
        r.setOffsetFlag("OPEN");
        r.setPrice(new BigDecimal("3500"));
        r.setQuantity(new BigDecimal("2"));
        r.setStrategyId("S1");
        r.setAccountId("A1");
        return r;
    }

    @Test
    @DisplayName("未过载：正常放行（passed=true）")
    void passesWhenCapacityAvailable() {
        RiskCheckResult result = service.check(req(), mock(RiskRule.RiskContext.class));
        assertThat(result.isPassed()).isTrue();
    }

    @Test
    @DisplayName("过载：限流拒绝新开仓，violations 含 OVERLOAD_LIMIT")
    void rejectsNewOpenWhenOverloaded() {
        when(degradeService.tryAcquireOrderPermit()).thenReturn(false);
        RiskCheckResult result = service.check(req(), mock(RiskRule.RiskContext.class));
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getViolations())
                .anySatisfy(v -> assertThat(v.ruleCode()).isEqualTo("OVERLOAD_LIMIT"));
    }

    @Test
    @DisplayName("过载时平仓指令放行（核心风险释放通道不限流）")
    void closeOrderBypassesRateLimit() {
        when(degradeService.tryAcquireOrderPermit()).thenReturn(false);
        TradeRequest close = req();
        close.setOffsetFlag("CLOSE");
        RiskCheckResult result = service.check(close, mock(RiskRule.RiskContext.class));
        assertThat(result.isPassed()).isTrue();
    }
}
