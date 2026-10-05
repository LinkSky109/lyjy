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
 * 网关注册路由规则。
 */
@Data
@TableName("gateway_route_rule")
public class GatewayRouteRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleId;

    private String ruleName;

    private String accountId;

    private String symbolRegex;

    private String orderType;

    private String gatewayId;

    private String fallbackId;

    private Integer priority;

    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
