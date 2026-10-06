package com.lhjypt.core.model.attribution;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 绩效归因报告（P2 模块二 §2.5.4）——对应 attribution_report，保留审计。
 */
@Data
@TableName("attribution_report")
public class AttributionReport {

    public static final String TYPE_BRINSON = "BRINSON";
    public static final String TYPE_FACTOR = "FACTOR";
    public static final String TYPE_EXECUTION = "EXECUTION";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String reportId;

    private String attrType;

    private String scopeType;

    private String scopeId;

    private LocalDate periodStart;

    private LocalDate periodEnd;

    private String resultJson;

    private String operator;

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}