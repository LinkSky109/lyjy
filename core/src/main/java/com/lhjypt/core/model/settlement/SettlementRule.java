package com.lhjypt.core.model.settlement;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 交收规则（P2 模块一 §1.4）——对应 settlement_rule。
 */
@Data
@TableName("settlement_rule")
public class SettlementRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleId;

    /** CN / HK / US */
    private String market;

    private String exchangeMatch;

    /** T+N */
    private Integer cycleDays;

    private String currency;

    private String remark;

    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}