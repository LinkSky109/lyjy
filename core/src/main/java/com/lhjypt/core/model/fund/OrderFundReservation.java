package com.lhjypt.core.model.fund;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单级资金冻结记录。
 */
@Data
@TableName("order_fund_reservation")
public class OrderFundReservation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderId;

    private String accountId;

    private BigDecimal reservedMargin;

    private BigDecimal reservedCommission;

    private BigDecimal releasedAmount;

    private BigDecimal consumedAmount;

    /** RESERVED / PARTIAL_CONSUMED / CONSUMED / RELEASED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
