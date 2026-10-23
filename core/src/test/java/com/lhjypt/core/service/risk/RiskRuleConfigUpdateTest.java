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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskRuleConfigUpdateTest {

    private RiskRuleConfigMapper mapper;
    private DynamicStubRule rule;
    private RiskRuleConfigService service;

    @BeforeEach
    void setUp() {
        mapper = mock(RiskRuleConfigMapper.class);
        rule = new DynamicStubRule();
        when(mapper.selectList(null)).thenReturn(List.of());
        service = new RiskRuleConfigService(mapper, List.of(rule));
        service.load();
    }

    @Test
    @DisplayName("阈值更新校验后应用到规则实例并落库")
    void updateAppliesThresholdsAndPersists() {
        when(mapper.selectOne(any())).thenReturn(null);

        RiskRuleConfigService.RuleView view = service.update(
                "POSITION_LIMIT", "{\"maxVolume\":50}", "WARN", "risk_officer");

        assertThat(rule.maxVolume).isEqualTo(50);
        assertThat(view.thresholdParams()).isEqualTo("{\"maxVolume\":50}");
        assertThat(view.defaultThresholdParams()).isEqualTo("{\"maxVolume\":100}");
        assertThat(view.editable()).isTrue();
        assertThat(view.action()).isEqualTo("WARN");
        ArgumentCaptor<RiskRuleConfig> captor = ArgumentCaptor.forClass(RiskRuleConfig.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getThresholdParams()).isEqualTo("{\"maxVolume\":50}");
        assertThat(captor.getValue().getAction()).isEqualTo("WARN");
    }

    @Test
    @DisplayName("非法阈值返回 400 且不落库")
    void invalidThresholdsDoNotPersist() {
        assertThatThrownBy(() -> service.update(
                "POSITION_LIMIT", "{\"maxVolume\":0}", null, "risk_officer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxVolume");
        verify(mapper, never()).insert(any());
        verify(mapper, never()).updateById(any());
        assertThat(rule.maxVolume).isEqualTo(100);
    }

    @Test
    @DisplayName("不支持动态参数的规则拒绝阈值编辑")
    void rejectsThresholdsForStaticRule() {
        RiskRuleConfigMapper otherMapper = mock(RiskRuleConfigMapper.class);
        when(otherMapper.selectList(null)).thenReturn(List.of());
        RiskRuleConfigService other = new RiskRuleConfigService(
                otherMapper, List.of(new StaticStubRule()));
        other.load();

        assertThatThrownBy(() -> other.update(
                "SELF_TRADE", "{\"x\":1}", null, "risk_officer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持阈值编辑");
    }

    private static class DynamicStubRule implements DynamicRiskRule {
        private int maxVolume = 100;

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

        @Override
        public String getDefaultThresholds() {
            return "{\"maxVolume\":100}";
        }

        @Override
        public void applyThresholds(String thresholdParams) {
            var node = RiskThresholds.parse(thresholdParams);
            this.maxVolume = RiskThresholds.positiveInt(node, "maxVolume", maxVolume);
        }
    }

    private static class StaticStubRule implements RiskRule {
        @Override
        public String getCode() {
            return "SELF_TRADE";
        }

        @Override
        public String getName() {
            return "自成交防护";
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
