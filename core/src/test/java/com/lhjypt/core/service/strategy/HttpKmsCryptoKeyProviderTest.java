package com.lhjypt.core.service.strategy;

import com.lhjypt.core.config.KeyStoreProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HTTP KMS 密钥提供者测试：本地 HttpServer 模拟密钥服务，验证取键、缓存与失败。
 */
class HttpKmsCryptoKeyProviderTest {

    private static final byte[] KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("从 KMS 获取 32 字节密钥并缓存（只请求一次）")
    void fetchesAndCachesKey() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        String body = "{\"key\":\"" + Base64.getEncoder().encodeToString(KEY) + "\"}";
        startServer(200, body, hits);
        HttpKmsCryptoKeyProvider provider = provider();

        byte[] first = provider.key("local-dev-1");
        byte[] second = provider.key("local-dev-1");

        assertThat(first).hasSize(32).isEqualTo(KEY);
        assertThat(second).isEqualTo(first);
        assertThat(hits.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("兼容 data.key 响应结构")
    void supportsNestedDataShape() throws Exception {
        String body = "{\"data\":{\"key\":\"" + Base64.getEncoder().encodeToString(KEY) + "\"}}";
        startServer(200, body, new AtomicInteger());
        assertThat(provider().key("k1")).isEqualTo(KEY);
    }

    @Test
    @DisplayName("KMS 非 200 时快速失败")
    void nonOkStatusFails() throws Exception {
        startServer(404, "{\"error\":\"not found\"}", new AtomicInteger());
        assertThatThrownBy(() -> provider().key("missing"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KMS 返回异常状态");
    }

    @Test
    @DisplayName("未配置 kms-url 时失败")
    void missingUrlFails() {
        KeyStoreProperties properties = new KeyStoreProperties();
        properties.setKmsUrl("");
        assertThatThrownBy(() -> new HttpKmsCryptoKeyProvider(properties).key("k"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kms-url");
    }

    private HttpKmsCryptoKeyProvider provider() {
        KeyStoreProperties properties = new KeyStoreProperties();
        properties.setProvider("kms");
        properties.setActiveKeyId("local-dev-1");
        properties.setKmsUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setKmsTimeoutMs(2000);
        return new HttpKmsCryptoKeyProvider(properties);
    }

    private void startServer(int status, String body, AtomicInteger hits) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/keys/", exchange -> {
            hits.incrementAndGet();
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
    }
}
