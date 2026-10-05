package com.lhjypt.core.model.strategy;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 策略操作审计日志（P1）——对应 strategy_audit_log，保留 ≥3 年，不可删改。
 */
@Data
@TableName("strategy_audit_log")
public class StrategyAuditLog {

    public static final String UPLOAD = "UPLOAD";
    public static final String DOWNLOAD = "DOWNLOAD";
    public static final String RUN = "RUN";
    public static final String STOP = "STOP";
    public static final String EDIT_PARAM = "EDIT_PARAM";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String strategyId;

    private String action;

    private String operator;

    private String detailJson;

    private String ipAddr;

    private LocalDateTime createTime;
}
