package com.lhjypt.core.service.strategy;

/**
 * 策略加密密钥提供者 SPI（P1）。
 *
 * <p>本地实现读取 {@code config/key-store.properties}；
 * 生产实现替换为 KMS/HSM（预留），上层 {@code StrategyCryptoService} 只依赖本接口。
 */
public interface CryptoKeyProvider {

    /** 当前活跃密钥 ID。 */
    String activeKeyId();

    /**
     * 按 keyId 获取 32 字节（AES-256）密钥。
     *
     * @throws IllegalStateException 密钥不存在且无法回退
     */
    byte[] key(String keyId);
}
