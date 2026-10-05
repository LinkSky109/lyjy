package com.lhjypt.core.model.gateway;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 交易网关注册信息。
 */
@Data
@TableName("gateway_info")
public class GatewayInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String gatewayId;

    /** MOCK / CTP / FIX */
    private String gatewayType;

    private String name;

    /** DOWN / CONNECTING / READY / DEGRADED */
    private String status;

    /** 连接参数，生产只保存密钥引用 */
    private String paramConfig;

    private Integer priority;

    private String healthPath;

    private LocalDateTime lastHealthAt;

    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
