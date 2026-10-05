package com.lhjypt.core.model.var;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * VaR 计算记录（P1）——对应 var_calculation 表，每次计算一行。
 */
@Data
@TableName("var_calculation")
public class VarCalculation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 计算唯一标识 */
    private String calcId;

    /** 作用域类型：PORTFOLIO / ACCOUNT / STRATEGY */
    private String scopeType;

    /** 作用域 ID（组合 ID / 账户 ID / 策略 ID） */
    private String scopeId;

    /** 计算方法：HISTORICAL / VAR_COV */
    private String method;

    /** 置信度，如 0.95 */
    private BigDecimal confidence;

    /** 持有期（天） */
    private Integer horizonDays;

    /** VaR 金额（正数表示可能损失） */
    private BigDecimal varValue;

    /** 损益序列概要 JSON */
    private String pnlHistJson;

    private LocalDateTime calcTime;
}
