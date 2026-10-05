package com.lhjypt.core.model.risk;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 风控处置执行记录（P1）
 * action：WARN(预警)/BLOCK_OPEN(限制开仓)/FORCE_CLOSE(强制平仓)
 * result：PENDING/SUCCESS/FAILED/REJECTED/RELEASED
 */
@Data
@TableName("risk_disposition_log")
public class RiskDispositionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 处置ID（唯一） */
    private String dispositionId;

    /** 触发的规则ID */
    private String triggerRule;

    /** 作用域类型：ACCOUNT/STRATEGY/SYMBOL */
    private String scopeType;

    /** 作用域ID */
    private String scopeId;

    /** 处置动作 */
    private String action;

    /** 处置参数JSON */
    private String params;

    /** 处置结果 */
    private String result;

    /** 确认人 */
    private String confirmBy;

    /** 说明 */
    private String message;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}