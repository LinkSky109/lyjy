package com.lhjypt.core.model.regulatory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 监管报送明细（P2）——对应 regulatory_report_item。
 */
@Data
@TableName("regulatory_report_item")
public class RegulatoryReportItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchId;

    private Integer rowNo;

    /** 明细 JSON，结构与报表类型相关 */
    private String contentJson;

    /** READY / EXCLUDED */
    private String status;

    private LocalDateTime createTime;
}