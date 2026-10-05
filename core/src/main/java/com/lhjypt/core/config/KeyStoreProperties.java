package com.lhjypt.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 策略加密密钥库配置（P1）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lhjypt.strategy.crypto")
public class KeyStoreProperties {

    /** 本地密钥库路径（不入 Git）。 */
    private String keyStorePath = "config/key-store.properties";

    /** 当前活跃 keyId。 */
    private String activeKeyId = "local-dev-1";

    /** 密钥库缺失时是否使用本地开发兜底密钥（仅本地可运行，生产应关闭）。 */
    private boolean allowDevFallback = true;
}
