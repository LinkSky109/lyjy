package com.lhjypt.core.service.strategy;

import com.lhjypt.core.config.KeyStoreProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyCryptoServiceTest {

    private StrategyCryptoService service;

    @BeforeEach
    void setUp() {
        KeyStoreProperties properties = new KeyStoreProperties();
        properties.setAllowDevFallback(true);
        properties.setActiveKeyId("local-dev-1");
        service = new StrategyCryptoService(new LocalCryptoKeyProvider(properties));
    }

    @Test
    @DisplayName("AES-256-GCM 加解密可还原，且每次密文不同（随机 IV）")
    void encryptDecryptRoundTrip() {
        String source = "class MyStrategy:\n    pass\n";

        String first = service.encrypt(source);
        String second = service.encrypt(source);

        assertThat(first).isNotEqualTo(second);
        assertThat(first).isNotEqualTo(source);
        assertThat(service.decrypt(first, "local-dev-1")).isEqualTo(source);
        assertThat(service.decrypt(second, "local-dev-1")).isEqualTo(source);
    }

    @Test
    @DisplayName("SHA-256 校验和稳定且为 64 位十六进制")
    void sha256IsStable() {
        String checksum = service.sha256("abc");
        assertThat(checksum).isEqualTo(service.sha256("abc"));
        assertThat(checksum).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("错误密钥无法解密")
    void wrongKeyFails() {
        String ciphertext = service.encrypt("secret");
        assertThatThrownBy(() -> service.decrypt(ciphertext, "another-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("解密失败");
    }

    @Test
    @DisplayName("密文被篡改后解密失败（GCM 完整性校验）")
    void tamperedCiphertextFails() {
        String ciphertext = service.encrypt("secret");
        byte[] payload = Base64.getDecoder().decode(ciphertext);
        payload[payload.length - 1] ^= 0x01;

        assertThatThrownBy(() -> service.decrypt(
                Base64.getEncoder().encodeToString(payload), "local-dev-1"))
                .isInstanceOf(IllegalStateException.class);
    }
}
