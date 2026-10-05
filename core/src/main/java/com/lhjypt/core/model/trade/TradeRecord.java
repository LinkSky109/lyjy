package com.lhjypt.core.model.trade;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 持久化成交记录。
 */
@Data
@TableName("trade_record")
public class TradeRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tradeId;

    private String gatewayId;

    private String gatewaySessionId;

    private String orderId;

    private String exchangeOrderId;

    private String accountId;

    private String strategyId;

    private String symbol;

    private String exchange;

    private String direction;

    private String offsetFlag;

    private BigDecimal price;

    private BigDecimal quantity;

    private BigDecimal turnover;

    private BigDecimal commission;

    private BigDecimal realizedPnl;

    private LocalDateTime tradeTime;

    private String tradingDay;

    private LocalDateTime createTime;
}
