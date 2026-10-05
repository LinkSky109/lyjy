package com.lhjypt.core.model.portfolio;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("portfolio_rebalance_job")
public class PortfolioRebalanceJob {

    public static final String PENDING = "PENDING";
    public static final String PREVIEW_READY = "PREVIEW_READY";
    public static final String RUNNING = "RUNNING";
    public static final String COMPLETED = "COMPLETED";
    public static final String PARTIALLY_COMPLETED = "PARTIALLY_COMPLETED";
    public static final String FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String jobId;

    private String portfolioId;

    private String triggerType;

    private String status;

    private String previewJson;

    private String executeMode;

    private BigDecimal diffTotal;

    private BigDecimal costEst;

    private LocalDateTime executedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
