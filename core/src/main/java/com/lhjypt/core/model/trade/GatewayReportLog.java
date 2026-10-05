package com.lhjypt.core.model.trade;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 网关回报幂等日志。
 */
@Data
@TableName("gateway_report_log")
public class GatewayReportLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String gatewayId;

    /** ORDER / TRADE */
    private String reportType;

    private String reportKey;

    private String payloadHash;

    /** RECEIVED / PROCESSED / FAILED */
    private String processStatus;

    private Integer retryCount;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
