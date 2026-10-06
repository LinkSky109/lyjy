package com.lhjypt.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 容量管理与自动伸缩配置（P2 模块五）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lhjypt.capacity")
public class CapacityProperties {

    /** 预警水位（%） */
    private double warnPct = 0.8;

    /** 扩容水位（%） */
    private double scalePct = 0.9;

    /** 是否在超扩容水位时触发 AutoScaler */
    private boolean autoscaleEnabled = true;

    /** noop（默认，本地）| k8s（生产预留） */
    private String autoscaleProvider = "noop";

    private int desiredMin = 1;

    private int desiredMax = 10;

    private long sampleIntervalMs = 30000;
}