package com.lhjypt.core.model.attribution;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Brinson 归因的单组分（分组/行业/风格桶）。
 */
@Data
public class BrinsonLine {

    private String group;

    private BigDecimal portfolioWeight;

    private BigDecimal portfolioReturn;

    private BigDecimal benchmarkWeight;

    private BigDecimal benchmarkReturn;
}