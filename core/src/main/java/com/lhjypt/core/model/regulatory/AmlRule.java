package com.lhjypt.core.model.regulatory;

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
 * AML 阈值规则（P2）——对应 aml_rule。
 */
@Data
@TableName("aml_rule")
public class AmlRule {

    public static final String LARGE_SINGLE = "LARGE_SINGLE";
    public static final String LARGE_CUMULATIVE = "LARGE_CUMULATIVE";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleId;

    private String ruleCode;

    private String name;

    private BigDecimal threshold;

    /** 累计窗口（秒），单笔规则为 0 */
    private Integer periodSeconds;

    private String severity;

    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}