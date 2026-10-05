package com.lhjypt.core.model.var;

import lombok.Data;

import java.math.BigDecimal;

/**
 * VaR 计算请求体（PRD §5.7）。
 */
@Data
public class VarComputeRequest {

    /** 作用域类型：PORTFOLIO / ACCOUNT / STRATEGY */
    private String scopeType;

    /** 作用域 ID */
    private String scopeId;

    /** 计算方法：HISTORICAL（默认）/ VAR_COV */
    private String method;

    /** 置信度，默认 0.95 */
    private BigDecimal confidence;

    /** 持有期（天），默认 1 */
    private Integer horizonDays;
}
