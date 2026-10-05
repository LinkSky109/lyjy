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

    /** 密钥库路径（不入 Git）。 */
    private String keyStorePath = "config/key-store.properties";

    /** 当前活跃 keyId。 */
    private String activeKeyId = "local-dev-1";

    /** 密钥库缺失时是否使用本地开发兜底密钥（仅本地可运行，生产应关闭）。 */
    private boolean allowDevFallback = true;

    /** 密钥提供者：local（本地密钥库，默认）| env（环境变量）| kms（HTTP KMS/密钥服务）。 */
    private String provider = "local";

    /** env provider 的环境变量前缀，环境变量名 = 前缀 + keyId 规范化。 */
    private String envKeyPrefix = "LHJYPT_STRATEGY_KEY_";

    /** kms provider 的密钥服务基址，如 https://kms.internal/api/v1（GET {url}/keys/{keyId}）。 */
    private String kmsUrl = "";

    /** kms provider 的访问令牌（Bearer），生产经 Secret 注入。 */
    private String kmsToken = "";

    /** kms provider 请求超时（毫秒）。 */
    private int kmsTimeoutMs = 3000;
}
