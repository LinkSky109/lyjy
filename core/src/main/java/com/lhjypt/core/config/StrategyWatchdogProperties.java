package com.lhjypt.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 策略自动值守配置（OBS-010）。 */
@Data
@Component
@ConfigurationProperties(prefix = "lhjypt.strategy.watchdog")
public class StrategyWatchdogProperties {

    /** 全局开关 */
    private boolean enabled = true;

    /** 值守扫描间隔（ms） */
    private long scanIntervalMs = 30_000;
}