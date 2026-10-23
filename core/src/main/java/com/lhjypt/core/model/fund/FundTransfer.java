package com.lhjypt.core.model.fund;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账户间资金划转台账。
 */
@Data
@TableName("fund_transfer")
public class FundTransfer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String transferNo;

    private String fromAccountId;

    private String toAccountId;

    private BigDecimal amount;

    private String currency;

    private String status;

    private String idempotencyKey;

    private String operator;

    private String remark;

    private BigDecimal fromBalanceBefore;

    private BigDecimal fromBalanceAfter;

    private BigDecimal toBalanceBefore;

    private BigDecimal toBalanceAfter;

    private LocalDateTime createTime;

    private LocalDateTime completeTime;
}
