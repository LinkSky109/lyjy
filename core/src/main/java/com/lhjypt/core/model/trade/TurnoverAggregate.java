package com.lhjypt.core.model.trade;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 成交额聚合（按账户），用于 AML 累计窗口统计。
 */
@Data
public class TurnoverAggregate {

    private String accountId;

    private BigDecimal turnover;
}