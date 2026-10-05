package com.lhjypt.core.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 持久化持仓。
 *
 * 业务唯一键：accountId + strategyId + symbol + direction。
 */
@Data
@TableName("position")
public class Position {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String accountId;

    private String strategyId;

    private String symbol;

    /** LONG / SHORT */
    private String direction;

    private BigDecimal quantity;

    private BigDecimal availableQuantity;

    private BigDecimal avgCost;

    private BigDecimal unrealizedPnl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
