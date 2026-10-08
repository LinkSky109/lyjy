package com.lhjypt.core.model.strategy;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 策略回撤熔断配置（OBS-016）——对应 strategy_circuit。 */
@Data
@TableName("strategy_circuit")
public class StrategyCircuit {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String strategyId;

    /** 触发阈值：从策略浮盈率峰值回撤的比率（0.05 = 5%） */
    private BigDecimal maxDrawdownPct;

    private Boolean enabled;

    /** 历史峰值浮盈率 */
    private BigDecimal peakPnlRate;

    private Boolean tripped;

    private LocalDateTime lastTripTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}