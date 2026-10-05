package com.lhjypt.core.service.strategy;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lhjypt.core.config.KeyStoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通过 HTTP 密钥服务（KMS/密钥网关）获取 AES-256 密钥（生产向）。
 *
 * <p>协议：{@code GET {kms-url}/keys/{keyId}}，响应 {@code {"key":"<Base64 32字节>"}}
 * 或 {@code {"data":{"key":"..."}}}；可选 {@code Authorization: Bearer <token>}。
 * 密钥在内存缓存，绝不写入日志。
 *
 * <p>未配置 {@code kms-url} 或缺 key 时快速失败；真实云 KMS（AWS/GCP/Vault）可据此 SPI 适配。
 */
@Component
@ConditionalOnProperty(name = "lhjypt.strategy.crypto.provider", havingValue = "kms")
public class HttpKmsCryptoKeyProvider implements CryptoKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpKmsCryptoKeyProvider.class);
    private static final int AES_256_KEY_BYTES = 32;

    private final KeyStoreProperties properties;
    private final HttpClient httpClient;
    private final ConcurrentHashMap<String, byte[]> cache = new ConcurrentHashMap<>();

    public HttpKmsCryptoKeyProvider(KeyStoreProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMillis()))
                .build();
    }

    @Override
    public String activeKeyId() {
        return properties.getActiveKeyId();
    }

    @Override
    public byte[] key(String keyId) {
        String id = keyId == null || keyId.isBlank() ? activeKeyId() : keyId;
        byte[] cached = cache.get(id);
        if (cached != null) {
            return cached;
        }
        String baseUrl = properties.getKmsUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("未配置 lhjypt.strategy.crypto.kms-url");
        }
        try {
            String url = baseUrl.replaceAll("/+$", "") + "/keys/"
                    + URLEncoder.encode(id, StandardCharsets.UTF_8);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMillis()))
                    .GET();
            if (properties.getKmsToken() != null && !properties.getKmsToken().isBlank()) {
                builder.header("Authorization", "Bearer " + properties.getKmsToken());
            }
            HttpResponse<String> response = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "KMS 返回异常状态 " + response.statusCode() + " keyId=" + id);
            }
            byte[] material = decode(response.body());
            cache.put(id, material);
            log.info("已从 KMS 加载策略密钥: keyId={} bytes={}", id, material.length);
            return material;
        } catch (IllegalStateException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException(
                    "KMS 获取密钥失败 keyId=" + id + ": " + error.getMessage(), error);
        }
    }

    private static byte[] decode(String body) {
        JSONObject json = JSON.parseObject(body);
        String key = json == null ? null : json.getString("key");
        if (key == null && json != null && json.getJSONObject("data") != null) {
            key = json.getJSONObject("data").getString("key");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("KMS 响应缺少 key 字段");
        }
        byte[] decoded = Base64.getDecoder().decode(key.trim());
        if (decoded.length != AES_256_KEY_BYTES) {
            throw new IllegalStateException("KMS 返回密钥长度非 32 字节: " + decoded.length);
        }
        return decoded;
    }

    private long timeoutMillis() {
        return Math.max(properties.getKmsTimeoutMs(), 100);
    }
}
