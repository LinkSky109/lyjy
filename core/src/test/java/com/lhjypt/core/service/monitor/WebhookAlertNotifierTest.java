package com.lhjypt.core.service.monitor;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lhjypt.core.model.monitor.AlertEvent;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebhookAlertNotifier 请求结构测试：用本地 JDK HttpServer 接收，断言实际发送的 JSON 字段。
 * 不依赖真实钉钉/企业微信，也无需 Spring 上下文。
 */
class WebhookAlertNotifierTest {

    private HttpServer server;
    private int port;
    private volatile String receivedBody;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            receivedBody = new String(body, StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private NotifyProperties properties(String type) {
        NotifyProperties p = new NotifyProperties();
        p.setEnabled(true);
        p.setType(type);
        p.setWebhookUrl("http://127.0.0.1:" + port + "/hook");
        p.setTimeoutMs(3000);
        return p;
    }

    private AlertEvent event() {
        AlertEvent e = new AlertEvent();
        e.setEventId("EVT-0001");
        e.setRuleId("AR1");
        e.setMetricKey("jvm.memory.used");
        e.setValue("90.00");
        e.setSeverity("WARN");
        e.setMessage("[WARN] 内存告警 - 当前值: 90.00 GT 阈值: 80.0");
        e.setStatus("OPEN");
        return e;
    }

    @Test
    @DisplayName("dingtalk 渠道发送文本消息结构")
    void dingtalkPayloadStructure() {
        WebhookAlertNotifier notifier = new WebhookAlertNotifier(properties("dingtalk"));

        notifier.notify(event());

        JSONObject body = JSON.parseObject(receivedBody);
        assertThat(body.getString("msgtype")).isEqualTo("text");
        assertThat(body.getJSONObject("text")).isNotNull();
        assertThat(body.getJSONObject("text").getString("content"))
                .contains("WARN").contains("内存告警");
    }

    @Test
    @DisplayName("generic 渠道发送扁平事件字段")
    void genericPayloadStructure() {
        WebhookAlertNotifier notifier = new WebhookAlertNotifier(properties("generic"));

        notifier.notify(event());

        JSONObject body = JSON.parseObject(receivedBody);
        assertThat(body.getString("metricKey")).isEqualTo("jvm.memory.used");
        assertThat(body.getString("value")).isEqualTo("90.00");
        assertThat(body.getString("severity")).isEqualTo("WARN");
        assertThat(body.getString("eventId")).isEqualTo("EVT-0001");
        assertThat(body.getString("status")).isEqualTo("OPEN");
        assertThat(body.getString("message")).contains("内存告警");
    }

    @Test
    @DisplayName("webhook 不可达时仅日志降级，不抛异常")
    void unreachableWebhookDoesNotThrow() {
        NotifyProperties p = properties("generic");
        p.setWebhookUrl("http://127.0.0.1:1/hook"); // 必定连接失败
        p.setTimeoutMs(500);
        WebhookAlertNotifier notifier = new WebhookAlertNotifier(p);

        notifier.notify(event()); // 不抛异常即通过
    }
}
