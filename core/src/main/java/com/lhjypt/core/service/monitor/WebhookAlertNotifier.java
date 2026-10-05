package com.lhjypt.core.service.monitor;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lhjypt.core.model.monitor.AlertEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Webhook 告警通知器（钉钉 / 企业微信 / 通用）。
 *
 * <p>仅在 {@code lhjypt.monitoring.notify.enabled=true} 时装配。发送失败或超时只记 warn，
 * 不抛异常——告警事件此时已落库，通知失败不影响主流程。</p>
 */
@Component
@ConditionalOnProperty(name = "lhjypt.monitoring.notify.enabled", havingValue = "true")
public class WebhookAlertNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookAlertNotifier.class);

    private final NotifyProperties properties;
    private final HttpClient httpClient;

    public WebhookAlertNotifier(NotifyProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(properties.getTimeoutMs(), 1)))
                .build();
    }

    @Override
    public void notify(AlertEvent event) {
        if (properties.getWebhookUrl() == null || properties.getWebhookUrl().isBlank()) {
            log.warn("告警通知已启用但未配置 webhook-url，跳过发送: eventId={}", event.getEventId());
            return;
        }
        try {
            String body = buildBody(normalizeType(properties.getType()), event);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getWebhookUrl()))
                    .timeout(Duration.ofMillis(Math.max(properties.getTimeoutMs(), 1)))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("告警 webhook 返回非 2xx: eventId={} status={} body={}",
                        event.getEventId(), response.statusCode(), response.body());
            } else {
                log.info("告警 webhook 发送成功: eventId={} type={}", event.getEventId(), properties.getType());
            }
        } catch (Exception e) {
            // 通知失败仅日志降级，告警已落库
            log.warn("告警 webhook 发送失败: eventId={} url={} error={}",
                    event.getEventId(), properties.getWebhookUrl(), e.getMessage());
        }
    }

    /**
     * 按渠道类型构造请求体。
     * dingtalk / wecom 使用机器人文本消息结构；generic 投递扁平事件字段。
     */
    static String buildBody(String type, AlertEvent event) {
        return switch (type) {
            case "dingtalk", "wecom" -> {
                JSONObject text = new JSONObject();
                text.put("content", content(event));
                JSONObject body = new JSONObject();
                body.put("msgtype", "text");
                body.put("text", text);
                yield JSON.toJSONString(body);
            }
            default -> {
                JSONObject body = new JSONObject();
                body.put("metricKey", event.getMetricKey());
                body.put("value", event.getValue());
                body.put("severity", event.getSeverity());
                body.put("message", event.getMessage());
                body.put("eventId", event.getEventId());
                body.put("status", event.getStatus());
                yield JSON.toJSONString(body);
            }
        };
    }

    private static String content(AlertEvent event) {
        return String.format("[%s] %s", event.getSeverity(), event.getMessage());
    }

    private static String normalizeType(String type) {
        return type == null ? "" : type.trim().toLowerCase();
    }
}
