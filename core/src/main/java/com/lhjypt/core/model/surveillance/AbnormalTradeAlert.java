package com.lhjypt.core.model.surveillance;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 异常交易告警（P1，TASK-P1-003）
 * status: OPEN / CONFIRMED / CLOSED
 * severity: INFO / WARN / CRITICAL
 */
@Data
@TableName("abnormal_trade_alert")
public class AbnormalTradeAlert {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 告警唯一号 */
    private String alertId;

    /** 规则：SELF_TRADE/FREQ_CANCEL/FALSE_ORDER/PUMP_DUMP */
    private String ruleCode;

    private String accountId;
    private String strategyId;
    private String symbol;

    /** 命中评分（0~1） */
    private BigDecimal score;

    /** 评分明细 JSON（窗口计数、比率、样本） */
    private String detailJson;

    private String severity;
    private String status;

    /** 处置人 / 处置说明（确认上报或关闭时填写） */
    private String handler;
    private String handleNote;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
