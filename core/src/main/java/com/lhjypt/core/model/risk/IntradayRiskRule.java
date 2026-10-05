package com.lhjypt.core.model.risk;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 事中风控规则配置（P1）
 * 规则类型：POSITION_LIMIT/LOSS_LIMIT/FUND_RISK/ORDER_RATE/VAR_LIMIT
 */
@Data
@TableName("intraday_risk_rule")
public class IntradayRiskRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则ID（唯一） */
    private String ruleId;

    /** 规则名称 */
    private String name;

    /** 规则类型 */
    private String ruleType;

    /** 是否启用 */
    private Boolean enabled;

    /** 适用账户（空=全部） */
    private String accountId;

    /** 适用策略（空=全部） */
    private String strategyId;

    /** 适用标的（空=全部） */
    private String symbol;

    /** 阈值参数JSON字符串 */
    private String thresholdParams;

    /** 触发动作级别：WARN/BLOCK/FORCE_CLOSE */
    private String actionLevel;

    /** 冷却时间（秒），同规则同作用域触发间隔 */
    private Integer cooldownSec;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}