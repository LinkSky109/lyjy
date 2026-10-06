package com.lhjypt.core.model.risk;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 风险预算分配与占用（P2 模块二 §2.3）——对应 risk_budget。
 */
@Data
@TableName("risk_budget")
public class RiskBudget {

    public static final String STATUS_OK = "OK";
    public static final String STATUS_WARN = "WARN";
    public static final String STATUS_BLOCK = "BLOCK";
    public static final String TYPE_VAR = "VAR";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String budgetId;

    private String scopeType;

    private String scopeId;

    private String budgetType;

    /** 预算上限 */
    private BigDecimal limitValue;

    /** 当前占用（由最近 VaR 刷新） */
    private BigDecimal currentValue;

    /** 占用率 = current/limit */
    private BigDecimal usageRate;

    /** OK / WARN / BLOCK */
    private String status;

    private Boolean enabled;

    private String remark;

    private String updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}