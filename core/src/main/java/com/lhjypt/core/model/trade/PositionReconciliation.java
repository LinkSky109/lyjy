package com.lhjypt.core.model.trade;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 系统持仓与柜台持仓对账记录。
 */
@Data
@TableName("position_reconciliation")
public class PositionReconciliation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String reconNo;

    private String accountId;

    private String strategyId;

    private String symbol;

    private String direction;

    private BigDecimal systemQuantity;

    private BigDecimal gatewayQuantity;

    private BigDecimal difference;

    /** MATCHED / MISMATCHED */
    private String status;

    private String reason;

    private LocalDateTime createTime;
}
