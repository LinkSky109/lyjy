package com.lhjypt.core.service.risk;

import com.lhjypt.core.mapper.RiskRuleConfigMapper;
import com.lhjypt.core.model.risk.RiskCheckResult;
import com.lhjypt.core.model.risk.RiskRuleConfig;
import com.lhjypt.core.model.trade.TradeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskRuleConfigServiceTest {

    private RiskRuleConfigMapper mapper;
    private RiskRuleConfigService service;

    @BeforeEach
    void setUp() {
        mapper = mock(RiskRuleConfigMapper.class);
        when(mapper.selectList(null)).thenReturn(List.of());
        service = new RiskRuleConfigService(mapper, List.of(new StubRule()));
        service.load();
    }

    @Test
    @DisplayName("无配置时沿用规则默认启用状态")
    void listFallsBackToRuleDefault() {
        List<RiskRuleConfigService.RuleView> rules = service.list();

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).ruleCode()).isEqualTo("POSITION_LIMIT");
        assertThat(rules.get(0).enabled()).isTrue();
        assertThat(rules.get(0).configured()).isFalse();
        assertThat(service.isEnabled("POSITION_LIMIT", true)).isTrue();
    }

    @Test
    @DisplayName("关闭规则写入配置并立即生效")
    void toggleCreatesConfigAndOverridesRuntime() {
        when(mapper.selectOne(any())).thenReturn(null);

        RiskRuleConfigService.RuleView view = service.toggle("POSITION_LIMIT", false);

        ArgumentCaptor<RiskRuleConfig> captor = ArgumentCaptor.forClass(RiskRuleConfig.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getEnabled()).isFalse();
        assertThat(captor.getValue().getRuleName()).isEqualTo("持仓限额");
        assertThat(view.enabled()).isFalse();
        assertThat(view.configured()).isTrue();
        assertThat(service.isEnabled("POSITION_LIMIT", true)).isFalse();
    }

    @Test
    @DisplayName("已有配置时更新而不是新增")
    void toggleUpdatesExistingConfig() {
        RiskRuleConfig existing = new RiskRuleConfig();
        existing.setId(9L);
        existing.setRuleCode("POSITION_LIMIT");
        existing.setRuleName("持仓限额");
        existing.setEnabled(true);
        when(mapper.selectOne(any())).thenReturn(existing);

        service.toggle("POSITION_LIMIT", false);

        verify(mapper).updateById(existing);
        assertThat(existing.getEnabled()).isFalse();
    }

    @Test
    @DisplayName("未知规则返回参数错误")
    void toggleRejectsUnknownRule() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.toggle("NOPE", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知风控规则");
    }

    private static class StubRule implements RiskRule {
        @Override
        public String getCode() {
            return "POSITION_LIMIT";
        }

        @Override
        public String getName() {
            return "持仓限额";
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void check(TradeRequest request, RiskContext context, RiskCheckResult result) {
        }
    }
}
