package com.lhjypt.core.service.strategy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 策略源码加解密（AES-256-GCM）与完整性校验（SHA-256）。
 *
 * <p>密文结构：{@code Base64(IV[12] ‖ ciphertext ‖ GCM tag[16])}。
 * 明文只在进程内存在，绝不写入日志、审计或数据库。
 */
@Service
@RequiredArgsConstructor
public class StrategyCryptoService {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final CryptoKeyProvider keyProvider;
    private final SecureRandom random = new SecureRandom();

    /** 当前活跃 keyId。 */
    public String activeKeyId() {
        return keyProvider.activeKeyId();
    }

    /** 加密源码，返回 Base64 密文。 */
    public String encrypt(String plaintext) {
        return encrypt(plaintext, activeKeyId());
    }

    public String encrypt(String plaintext, String keyId) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(keyProvider.key(keyId), "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception error) {
            throw new IllegalStateException("策略加密失败", error);
        }
    }

    /** 解密 Base64 密文，返回源码明文（仅限进程内使用）。 */
    public String decrypt(String ciphertextBase64, String keyId) {
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertextBase64);
            if (payload.length <= IV_BYTES) {
                throw new IllegalArgumentException("密文长度非法");
            }
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(keyProvider.key(keyId), "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new IllegalStateException("策略解密失败（密钥不匹配或密文被篡改）", error);
        }
    }

    /** 源码 SHA-256（十六进制）。 */
    public String sha256(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("计算校验和失败", error);
        }
    }
}
