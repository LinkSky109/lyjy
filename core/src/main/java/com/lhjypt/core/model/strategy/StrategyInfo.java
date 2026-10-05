package com.lhjypt.core.model.strategy;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 策略注册表（P1）——对应 strategy_info。
 */
@Data
@TableName("strategy_info")
public class StrategyInfo {

    public static final String OFFLINE = "OFFLINE";
    public static final String RUNNING = "RUNNING";
    public static final String ERROR = "ERROR";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String strategyId;

    private String name;

    private String owner;

    private String status;

    private String lang;

    private String riskProfile;

    private String paramsJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
