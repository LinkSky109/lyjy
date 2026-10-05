package com.lhjypt.core.service.strategy;

import com.lhjypt.core.config.KeyStoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Locale;
import java.util.function.Function;

/**
 * 环境变量密钥提供者（生产向）：从进程环境变量读取 AES-256 密钥。
 *
 * <p>生产由 KMS / Secret Manager 在容器启动前把密钥注入环境变量，
 * 进程只读取、不落盘。环境变量名 = {@link KeyStoreProperties#getEnvKeyPrefix()}
 * + keyId 规范化为 {@code [A-Z0-9_]}（大写、非字母数字替换为下划线），
 * 例如 keyId {@code local-dev-1} 对应 {@code LHJYPT_STRATEGY_KEY_LOCAL_DEV_1}。
 *
 * <p>安全约定：密钥缺失或 Base64 解码后非 32 字节一律快速失败，
 * <b>不做任何回退兜底</b>，避免生产静默降级为弱密钥。
 *
 * <p>SPI 已预留：如需接入 KMS/HSM（如 AWS KMS、Vault、PKCS#11），
 * 新增一个 {@link CryptoKeyProvider} 实现并调整
 * {@code lhjypt.strategy.crypto.provider} 即可，上层
 * {@link StrategyCryptoService} 无需改动。
 *
 * <p>可通过构造器注入 {@link Function} 以便单元测试（默认 {@link System#getenv}）。
 */
@Component
@ConditionalOnProperty(name = "lhjypt.strategy.crypto.provider", havingValue = "env")
public class EnvCryptoKeyProvider implements CryptoKeyProvider {

    private static final int AES_256_KEY_BYTES = 32;
    private static final String DEFAULT_ENV_PREFIX = "LHJYPT_STRATEGY_KEY_";

    private final KeyStoreProperties properties;
    private final Function<String, String> env;

    @Autowired
    public EnvCryptoKeyProvider(KeyStoreProperties properties) {
        this(properties, System::getenv);
    }

    public EnvCryptoKeyProvider(KeyStoreProperties properties, Function<String, String> env) {
        this.properties = properties;
        this.env = env == null ? System::getenv : env;
    }

    @Override
    public String activeKeyId() {
        return properties.getActiveKeyId();
    }

    @Override
    public byte[] key(String keyId) {
        String id = keyId == null || keyId.isBlank() ? activeKeyId() : keyId;
        String envName = envName(id);
        String value = env.apply(envName);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("策略密钥环境变量未配置: " + envName);
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(value.trim());
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("策略密钥 Base64 解码失败: " + envName, error);
        }
        if (decoded.length != AES_256_KEY_BYTES) {
            throw new IllegalStateException(
                    "策略密钥长度必须为 " + AES_256_KEY_BYTES + " 字节: "
                            + envName + "（实际 " + decoded.length + " 字节）");
        }
        return decoded;
    }

    /** 环境变量名：(前缀 + keyId) 转大写，非字母数字替换为下划线。 */
    private String envName(String keyId) {
        String prefix = properties.getEnvKeyPrefix();
        if (prefix == null) {
            prefix = DEFAULT_ENV_PREFIX;
        }
        return (prefix + keyId).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
    }
}
