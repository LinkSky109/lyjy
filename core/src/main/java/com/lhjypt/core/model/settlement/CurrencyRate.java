package com.lhjypt.core.model.settlement;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 汇率（P2 模块一 §1.1）——对应 currency_rate。
 */
@Data
@TableName("currency_rate")
public class CurrencyRate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String base;

    private String quote;

    private BigDecimal rate;

    private LocalDate rateDate;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}