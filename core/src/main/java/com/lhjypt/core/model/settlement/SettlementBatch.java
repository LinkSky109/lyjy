package com.lhjypt.core.model.settlement;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日终清算批次。
 */
@Data
@TableName("settlement_batch")
public class SettlementBatch {

    public static final String CREATED = "CREATED";
    public static final String RUNNING = "RUNNING";
    public static final String REVIEWING = "REVIEWING";
    public static final String CLOSED = "CLOSED";
    public static final String FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchId;

    private LocalDate tradeDate;

    private String status;

    private Integer accountCount;

    private BigDecimal totalPnl;

    private BigDecimal totalFee;

    private Integer totalDiff;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
