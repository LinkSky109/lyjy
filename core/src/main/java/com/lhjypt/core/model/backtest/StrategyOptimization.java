package com.lhjypt.core.model.backtest;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 策略优化存档（OBS-012）——对应 strategy_optimization。 */
@Data
@TableName("strategy_optimization")
public class StrategyOptimization {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchId;

    private String symbol;

    private String frequency;

    private String mode;

    private String objective;

    private Integer bestFast;

    private Integer bestSlow;

    private BigDecimal bestReturn;

    private BigDecimal bestSharpe;

    private BigDecimal bestDrawdown;

    private BigDecimal baselineReturn;

    private Integer barsUsed;

    private String startTime;

    private String endTime;

    private String runsJson;

    private LocalDateTime createTime;
}