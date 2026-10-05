package com.lhjypt.core.service.strategy;

import com.lhjypt.core.config.KeyStoreProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 环境变量密钥提供者测试：通过注入 env 函数模拟环境变量，不依赖真实 OS 环境。
 */
class EnvCryptoKeyProviderTest {

    private static final int AES_256_KEY_BYTES = 32;
    private static final String PREFIX = "LHJYPT_STRATEGY_KEY_";
    private static final String KEY_ID = "local-dev-1";
    private static final String ENV_NAME = "LHJYPT_STRATEGY_KEY_LOCAL_DEV_1";

    @Test
    @DisplayName("合法 32 字节密钥：加密解密往返一致")
    void roundTripWithValidEnvKey() {
        byte[] key = new byte[AES_256_KEY_BYTES];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (i * 7 + 1);
        }
        EnvCryptoKeyProvider provider = provider(Map.of(
                ENV_NAME, Base64.getEncoder().encodeToString(key)));
        StrategyCryptoService service = new StrategyCryptoService(provider);

        String source = "class Demo:\n    pass\n";
        String ciphertext = service.encrypt(source);

        assertThat(provider.activeKeyId()).isEqualTo(KEY_ID);
        assertThat(service.decrypt(ciphertext, KEY_ID)).isEqualTo(source);
        assertThat(provider.key(KEY_ID)).hasSize(AES_256_KEY_BYTES);
    }

    @Test
    @DisplayName("keyId 规范化：大写并把非字母数字替换为下划线")
    void envNameNormalization() {
        EnvCryptoKeyProvider provider = provider(Map.of(
                ENV_NAME, Base64.getEncoder().encodeToString(new byte[AES_256_KEY_BYTES])));

        assertThat(provider.key("Local-Dev-1")).hasSize(AES_256_KEY_BYTES);
        assertThat(provider.key(null)).hasSize(AES_256_KEY_BYTES);
    }

    @Test
    @DisplayName("密钥缺失：抛异常且不回退兜底")
    void missingKeyFails() {
        EnvCryptoKeyProvider provider = provider(Map.of());

        assertThatThrownBy(() -> provider.key(KEY_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未配置");
    }

    @Test
    @DisplayName("长度非 32 字节：拒绝")
    void invalidLengthFails() {
        EnvCryptoKeyProvider provider = provider(Map.of(
                ENV_NAME, Base64.getEncoder().encodeToString(new byte[16])));

        assertThatThrownBy(() -> provider.key(KEY_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 字节");
    }

    @Test
    @DisplayName("Base64 非法：拒绝")
    void invalidBase64Fails() {
        EnvCryptoKeyProvider provider = provider(Map.of(ENV_NAME, "not-base64!!"));

        assertThatThrownBy(() -> provider.key(KEY_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    private static EnvCryptoKeyProvider provider(Map<String, String> env) {
        KeyStoreProperties properties = new KeyStoreProperties();
        properties.setActiveKeyId(KEY_ID);
        properties.setEnvKeyPrefix(PREFIX);
        Map<String, String> copy = new HashMap<>(env);
        return new EnvCryptoKeyProvider(properties, copy::get);
    }
}
