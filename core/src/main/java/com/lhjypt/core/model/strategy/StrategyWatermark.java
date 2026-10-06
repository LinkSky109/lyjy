package com.lhjypt.core.model.strategy;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 策略水印与运行环境绑定（P2 模块三 §3.4）——对应 strategy_watermark。
 */
@Data
@TableName("strategy_watermark")
public class StrategyWatermark {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String strategyId;

    private Integer version;

    /** 水印令牌（溯源键，唯一）。 */
    private String token;

    private String owner;

    private String uploadedBy;

    /** 含水印源码 SHA-256。 */
    private String sourceHash;

    /** 上传时宿主环境指纹，运行前比对实现环境绑定。 */
    private String envFingerprint;

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}