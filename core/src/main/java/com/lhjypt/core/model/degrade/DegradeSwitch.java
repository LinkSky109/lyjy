package com.lhjypt.core.model.degrade;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 降级开关（P1，TASK-P1-004）
 * level: OFF / ON
 */
@Data
@TableName("degrade_switch")
public class DegradeSwitch {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 开关键，如 degrade.market.backup */
    private String switchKey;

    /** 展示名 */
    private String label;

    /** OFF / ON */
    private String level;

    /** 最近操作人 */
    private String operator;

    /** 操作原因 */
    private String reason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
