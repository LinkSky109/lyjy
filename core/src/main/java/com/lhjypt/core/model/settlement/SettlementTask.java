package com.lhjypt.core.model.settlement;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 资金交收任务（P2 模块一 §1.4）——对应 settlement_task，状态机待交收/交收中/已交收/失败。
 */
@Data
@TableName("settlement_task")
public class SettlementTask {

    public static final String PENDING = "PENDING";
    public static final String SETTLING = "SETTLING";
    public static final String SETTLED = "SETTLED";
    public static final String FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskId;

    private String tradeId;

    private String accountId;

    private String symbol;

    private String exchange;

    private String currency;

    private Integer cycleDays;

    private LocalDate tradeDate;

    private LocalDate expectedDate;

    private BigDecimal amount;

    private String status;

    private String settledBy;

    private LocalDateTime settledAt;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}