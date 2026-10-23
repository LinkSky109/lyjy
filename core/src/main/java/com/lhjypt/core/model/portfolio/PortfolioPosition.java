package com.lhjypt.core.model.portfolio;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("portfolio_position")
public class PortfolioPosition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String portfolioId;

    private String symbol;

    private String exchange;

    private String direction;

    private BigDecimal targetQty;

    private BigDecimal curQty;

    private BigDecimal weightLimit;

    /** 策略归属（用于组合贡献归因，可空）。 */
    private String strategyId;

    private LocalDateTime updateTime;
}
