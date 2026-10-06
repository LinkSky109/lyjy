package com.lhjypt.core.model.attribution;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 交易执行归因请求（P2 模块二 §2.5.3）：执行缺口 IS 分解。
 */
@Data
public class ExecutionRequest {

    /** BUY / SELL */
    private String side;

    /** 决策价（下单前基准价） */
    private BigDecimal decisionPrice;

    /** 到达价（委托进入市场时价） */
    private BigDecimal arrivalPrice;

    private BigDecimal fillPrice;

    private BigDecimal fillQuantity;

    /** 收盘价（未成交部分的机会成本基准，可空） */
    private BigDecimal closePrice;

    private BigDecimal commission;

    private String scopeType;
    private String scopeId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
}