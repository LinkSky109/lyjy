package com.lhjypt.core.model.var;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * VaR 计算结果视图（PRD §5.7）。
 */
@Data
public class VarResult {

    private String calcId;

    private String scopeType;

    private String scopeId;

    private String method;

    private BigDecimal confidence;

    private Integer horizonDays;

    /** VaR 金额（正数表示可能损失） */
    private BigDecimal varValue;

    /** 参与计算的收益样本数 */
    private int sampleCount;

    /** 组合日损益均值 */
    private BigDecimal meanPnl;

    /** 组合日损益标准差 */
    private BigDecimal stdPnl;

    /** 分位数处损益（历史模拟）或正态分位损益（方差协方差） */
    private BigDecimal percentilePnl;

    private BigDecimal minPnl;

    private BigDecimal maxPnl;

    /** 组合风险预算上限（仅 PORTFOLIO 作用域且配置存在时非空） */
    private BigDecimal riskBudgetLimit;

    /** 是否突破组合风险预算 */
    private boolean budgetBreached;

    private LocalDateTime calcTime;
}
