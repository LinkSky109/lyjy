package com.lhjypt.core.model.attribution;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Brinson 归因请求（P2 模块二 §2.5.1）。
 */
@Data
public class BrinsonRequest {

    /** 资产分组明细：组合权重/收益 vs 基准权重/收益。 */
    private List<BrinsonLine> lines;

    private String scopeType;
    private String scopeId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
}