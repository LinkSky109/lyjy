package com.lhjypt.core.model.settlement;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 清算对账差异工单。
 */
@Data
@TableName("settlement_diff_item")
public class SettlementDiffItem {

    public static final String OPEN = "OPEN";
    public static final String ADJUSTED = "ADJUSTED";
    public static final String CLOSED = "CLOSED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String diffId;

    private String batchId;

    private String accountId;

    private String fieldName;

    private BigDecimal systemValue;

    private BigDecimal counterValue;

    private BigDecimal diffValue;

    private BigDecimal tolerance;

    private String status;

    private String adjustBy;

    private String adjustNote;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
