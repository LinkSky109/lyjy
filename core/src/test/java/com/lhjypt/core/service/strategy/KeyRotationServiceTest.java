package com.lhjypt.core.service.strategy;

import com.lhjypt.core.mapper.StrategyAuditLogMapper;
import com.lhjypt.core.mapper.StrategyCryptoMapper;
import com.lhjypt.core.model.strategy.StrategyAuditLog;
import com.lhjypt.core.model.strategy.StrategyCrypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeyRotationServiceTest {

    private static final String KEY_K1 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="; // 32B
    private static final String KEY_K2 = "QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVowMTIzNDU="; // 另一把 32B

    private StrategyCryptoMapper cryptoMapper;
    private StrategyAuditLogMapper auditMapper;
    private StrategyCryptoService cryptoService;
    private KeyRotationService service;

    /** 固定双密钥 provider（测试用，避免依赖文件/环境）。 */
    private static CryptoKeyProvider twoKeyProvider() {
        return new CryptoKeyProvider() {
            @Override
            public String activeKeyId() {
                return "k1";
            }

            @Override
            public byte[] key(String keyId) {
                if ("k1".equals(keyId)) {
                    return java.util.Base64.getDecoder().decode(KEY_K1);
                }
                if ("k2".equals(keyId)) {
                    return java.util.Base64.getDecoder().decode(KEY_K2);
                }
                throw new IllegalStateException("未知密钥: " + keyId);
            }
        };
    }

    @BeforeEach
    void setUp() {
        cryptoMapper = mock(StrategyCryptoMapper.class);
        auditMapper = mock(StrategyAuditLogMapper.class);
        cryptoService = new StrategyCryptoService(twoKeyProvider());
        service = new KeyRotationService(cryptoMapper, cryptoService, twoKeyProvider(), auditMapper);
    }

    private StrategyCrypto cryptoOf(String strategyId, String plaintext) {
        StrategyCrypto crypto = new StrategyCrypto();
        crypto.setStrategyId(strategyId);
        crypto.setVersion(1);
        crypto.setAlgo("AES_256_GCM");
        crypto.setCiphertext(cryptoService.encrypt(plaintext, "k1"));
        crypto.setKeyId("k1");
        crypto.setChecksum(cryptoService.sha256(plaintext));
        return crypto;
    }

    @Test
    @DisplayName("轮换：旧密钥密文重加密为新密钥，可解密且审计留痕")
    void rotatesAllToNewKey() {
        StrategyCrypto a = cryptoOf("S1", "class A:\n pass\n");
        StrategyCrypto b = cryptoOf("S2", "class B:\n pass\n");
        when(cryptoMapper.selectList(null)).thenReturn(List.of(a, b));

        Map<String, Object> result = service.rotate("k2", "admin", "127.0.0.1");

        assertThat(result.get("rotated")).isEqualTo(2);
        assertThat(result.get("failed")).isEqualTo(0);
        assertThat(a.getKeyId()).isEqualTo("k2");
        // 可解密（新密钥）且内容一致
        assertThat(cryptoService.decrypt(a.getCiphertext(), a.getKeyId()))
                .isEqualTo("class A:\n pass\n");
        verify(cryptoMapper, times(2)).updateById(any(StrategyCrypto.class));

        ArgumentCaptor<StrategyAuditLog> captor = ArgumentCaptor.forClass(StrategyAuditLog.class);
        verify(auditMapper).insert(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("KEY_ROTATE");
        assertThat(captor.getValue().getDetailJson()).contains("\"rotated\":2");
    }

    @Test
    @DisplayName("已是新密钥的记录跳过；未知新密钥快速失败（不轮换）")
    void skipsAndRejectsUnknownKey() {
        StrategyCrypto already = cryptoOf("S3", "x");
        already.setKeyId("k2");
        when(cryptoMapper.selectList(null)).thenReturn(List.of(already));

        Map<String, Object> result = service.rotate("k2", "admin", null);
        assertThat(result.get("skipped")).isEqualTo(1);
        assertThat(result.get("rotated")).isEqualTo(0);
        verify(cryptoMapper, never()).updateById(any(StrategyCrypto.class));

        assertThatThrownBy(() -> service.rotate("nope", "admin", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("新密钥不可用");
    }

    @Test
    @DisplayName("状态：活跃密钥 + 各密钥使用计数")
    void statusShowsUsage() {
        when(cryptoMapper.selectKeyUsage()).thenReturn(List.of(
                Map.of("keyId", "k1", "cnt", 3L)));
        Map<String, Object> status = service.status();
        assertThat(status.get("activeKeyId")).isEqualTo("k1");
        assertThat((List<?>) status.get("usage")).hasSize(1);
    }
}