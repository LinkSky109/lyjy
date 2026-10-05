package com.lhjypt.core.service.monitor;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 告警通知渠道配置（PRD §10.2）。
 *
 * <p>默认关闭，仅落库 + 日志；生产可通过环境变量开启并指定 webhook。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "lhjypt.monitoring.notify")
public class NotifyProperties {

    /** 是否启用外部通知渠道；关闭时使用日志通知器（日志降级）。 */
    private boolean enabled = false;

    /** 通知类型：dingtalk | wecom | generic。 */
    private String type = "generic";

    /** Webhook 地址（钉钉/企业微信机器人或通用接收端）。 */
    private String webhookUrl = "";

    /** HTTP 超时（毫秒）。 */
    private int timeoutMs = 3000;
}
