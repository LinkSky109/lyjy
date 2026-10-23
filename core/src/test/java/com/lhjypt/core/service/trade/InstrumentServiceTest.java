package com.lhjypt.core.service.trade;

import com.lhjypt.core.config.InstrumentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InstrumentServiceTest {

    private InstrumentService service;

    @BeforeEach
    void setUp() {
        InstrumentProperties properties = new InstrumentProperties();
        InstrumentProperties.Spec spec = new InstrumentProperties.Spec();
        spec.setName("螺纹钢2410");
        spec.setExchange("SHFE");
        spec.setPriceTick(new BigDecimal("1"));
        spec.setContractMultiplier(new BigDecimal("10"));
        spec.setMaxQty(new BigDecimal("200"));
        properties.setContracts(Map.of("rb2410", spec));
        service = new InstrumentService(properties);
    }

    @Test
    @DisplayName("已配置合约返回乘数与数量限制")
    void findsConfiguredContract() {
        InstrumentService.InstrumentView view = service.find("rb2410").orElseThrow();

        assertThat(view.symbol()).isEqualTo("rb2410");
        assertThat(view.exchange()).isEqualTo("SHFE");
        assertThat(view.contractMultiplier()).isEqualByComparingTo("10");
        assertThat(view.maxQty()).isEqualByComparingTo("200");
        assertThat(view.trading()).isTrue();
    }

    @Test
    @DisplayName("未配置或空代码返回 empty")
    void returnsEmptyForUnknownContract() {
        assertThat(service.find("unknown")).isEmpty();
        assertThat(service.find("  ")).isEmpty();
        assertThat(service.find(null)).isEmpty();
    }

    @Test
    @DisplayName("列表返回全部配置合约")
    void listsConfiguredContracts() {
        assertThat(service.list()).hasSize(1);
    }

    @Test
    @DisplayName("按交易所与关键字搜索合约")
    void searchesByExchangeAndKeyword() {
        assertThat(service.search("SHFE", "螺纹")).hasSize(1);
        assertThat(service.search("CFFEX", null)).isEmpty();
        assertThat(service.search(null, "RB241")).hasSize(1);
        assertThat(service.search(null, "unknown")).isEmpty();
    }

    @Test
    @DisplayName("默认保证金比例 10%")
    void exposesDefaultMarginRatio() {
        assertThat(service.find("rb2410").orElseThrow().marginRatio())
                .isEqualByComparingTo("0.1");
    }
}
