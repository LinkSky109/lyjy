package com.lhjypt.core.model.regulatory;

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
 * 监管报送批次（P2）——对应 regulatory_report_batch。
 */
@Data
@TableName("regulatory_report_batch")
public class RegulatoryReportBatch {

    public static final String GENERATING = "GENERATING";
    public static final String READY = "READY";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchId;

    /** LARGE_TRADE / DAILY_TRADE */
    private String reportType;

    private LocalDate reportDate;

    private String status;

    private Integer totalItems;

    private BigDecimal totalAmount;

    private String fileName;

    private String errorMessage;

    private String submitBy;

    private LocalDateTime submitTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}