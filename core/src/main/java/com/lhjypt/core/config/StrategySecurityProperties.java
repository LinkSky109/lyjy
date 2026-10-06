package com.lhjypt.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 策略安全加固配置（P2 知识产权保护）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lhjypt.strategy.security")
public class StrategySecurityProperties {

    /** 运行前是否校验环境绑定指纹；关闭后不校验（生产建议开启）。 */
    private boolean envBindingEnabled = true;
}