package com.lhjypt.core.model.regulatory;

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
 * AML 告警（P2）——对应 aml_alert。
 */
@Data
@TableName("aml_alert")
public class AmlAlert {

    public static final String OPEN = "OPEN";
    public static final String REPORTED = "REPORTED";
    public static final String CLOSED = "CLOSED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String alertId;

    private String ruleCode;

    /** LARGE_SINGLE / LARGE_CUMULATIVE */
    private String mode;

    private String accountId;

    private String strategyId;

    private String symbol;

    private BigDecimal amount;

    private BigDecimal threshold;

    private String evidence;

    private String severity;

    private String status;

    private String handler;

    private String handleNote;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}