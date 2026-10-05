package com.lhjypt.core.service.strategy;

import com.lhjypt.core.config.KeyStoreProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 本地密钥库实现：读取 {@code config/key-store.properties}（keyId=Base64(32字节)）。
 * 文件缺失时按 keyId 派生本地开发兜底密钥，保证本地可运行；生产替换为 KMS/HSM。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lhjypt.strategy.crypto.provider",
        havingValue = "local", matchIfMissing = true)
public class LocalCryptoKeyProvider implements CryptoKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalCryptoKeyProvider.class);
    private static final int AES_256_KEY_BYTES = 32;
    private static final String DEV_SALT = "lhjypt-local-dev-key:";

    private final KeyStoreProperties properties;
    private volatile Map<String, byte[]> keys;

    @Override
    public String activeKeyId() {
        return properties.getActiveKeyId();
    }

    @Override
    public byte[] key(String keyId) {
        String id = keyId == null || keyId.isBlank() ? activeKeyId() : keyId;
        byte[] key = loadKeys().get(id);
        if (key != null) {
            return key;
        }
        if (properties.isAllowDevFallback()) {
            log.warn("密钥库无 keyId={}，使用本地开发兜底密钥（生产应配置 KMS/HSM）", id);
            return devKey(id);
        }
        throw new IllegalStateException("密钥不存在: " + id);
    }

    private Map<String, byte[]> loadKeys() {
        if (keys != null) {
            return keys;
        }
        synchronized (this) {
            if (keys != null) {
                return keys;
            }
            Map<String, byte[]> loaded = new HashMap<>();
            Path path = Path.of(properties.getKeyStorePath());
            if (Files.exists(path)) {
                try (InputStream input = Files.newInputStream(path)) {
                    Properties props = new Properties();
                    props.load(input);
                    for (String name : props.stringPropertyNames()) {
                        byte[] decoded = Base64.getDecoder().decode(props.getProperty(name).trim());
                        if (decoded.length == AES_256_KEY_BYTES) {
                            loaded.put(name, decoded);
                        } else {
                            log.warn("密钥 {} 长度非 32 字节，忽略", name);
                        }
                    }
                } catch (Exception error) {
                    log.warn("加载密钥库失败 {}: {}", path, error.getMessage());
                }
            }
            keys = loaded;
            return loaded;
        }
    }

    private static byte[] devKey(String keyId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest((DEV_SALT + keyId).getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IllegalStateException("派生本地开发密钥失败", error);
        }
    }
}
