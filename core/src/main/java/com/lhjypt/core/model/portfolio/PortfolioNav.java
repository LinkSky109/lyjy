package com.lhjypt.core.model.portfolio;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 组合净值快照（P5-001）。
 * 当前以账户权益作为净值口径，后续引入份额后可归一化为单位净值。
 */
@Data
@TableName("portfolio_nav")
public class PortfolioNav {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String portfolioId;

    private LocalDate navDate;

    private BigDecimal nav;

    private BigDecimal equity;

    private BigDecimal cash;

    private String source;

    private LocalDateTime createTime;
}
