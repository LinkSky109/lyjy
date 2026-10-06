package com.lhjypt.core.model.attribution;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 风险因子暴露归因请求（P2 模块二 §2.5.2）。
 */
@Data
public class FactorRequest {

    /** 标的 → 组合权重 */
    private Map<String, BigDecimal> weights;

    /** 标的 → 因子暴露（factor → 暴露度） */
    private Map<String, Map<String, BigDecimal>> exposures;

    private String scopeType;
    private String scopeId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
}