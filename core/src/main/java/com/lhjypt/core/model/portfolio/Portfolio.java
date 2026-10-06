package com.lhjypt.core.model.portfolio;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("portfolio")
public class Portfolio {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String portfolioId;

    private String name;

    private String owner;

    private String status;

    private String baseCurrency;

    private String configJson;

    /** 所属租户（P2 多租户横切）。 */
    private String tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
