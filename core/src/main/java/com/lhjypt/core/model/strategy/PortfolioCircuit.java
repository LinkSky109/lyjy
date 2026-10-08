package com.lhjypt.core.model.strategy;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 组合级风控配置（OBS-018）——对应 portfolio_circuit。 */
@Data
@TableName("portfolio_circuit")
public class PortfolioCircuit {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 组合标识（如 PF-BTC-ETH） */
    private String portfolioKey;

    /** 成员策略 ID（JSON 数组） */
    private String memberStrategies;

    private BigDecimal maxDrawdownPct;

    private Boolean enabled;

    private BigDecimal peakPnlRate;

    private Boolean tripped;

    private LocalDateTime lastTripTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}