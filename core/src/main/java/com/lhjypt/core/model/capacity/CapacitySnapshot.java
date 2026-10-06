package com.lhjypt.core.model.capacity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 容量水位快照（P2 模块五）——对应 capacity_snapshot。
 */
@Data
@TableName("capacity_snapshot")
public class CapacitySnapshot {

    public static final String NORMAL = "NORMAL";
    public static final String WARN = "WARN";
    public static final String SCALE = "SCALE";

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDateTime sampleTime;

    private BigDecimal cpuPct;

    private BigDecimal memPct;

    private BigDecimal diskPct;

    private Integer activeStrategies;

    private Integer queueDepth;

    private Integer inboundTps;

    private BigDecimal loadPct;

    private String status;

    private Integer desiredReplicas;

    private String detail;
}