package com.lhjypt.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 合约元数据配置（最小可行：配置文件维护，后续可迁移到 instrument_info 表）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lhjypt.instruments")
public class InstrumentProperties {

    private Map<String, Spec> contracts = new LinkedHashMap<>();

    @Data
    public static class Spec {
        private String name;
        private String exchange;
        private String instrumentType = "FUTURE";
        private BigDecimal priceTick;
        private BigDecimal contractMultiplier;
        private String currency = "CNY";
        private BigDecimal minQty;
        private BigDecimal maxQty;
        private BigDecimal qtyStep;
        private BigDecimal marginRatio = new BigDecimal("0.1");
        private BigDecimal limitUp;
        private BigDecimal limitDown;
        private boolean trading = true;
    }
}
