package com.lhjypt.core.service.monitor;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.lhjypt.core.model.monitor.AlertEvent;
import com.lhjypt.core.model.monitor.AlertRule;
import org.springframework.stereotype.Service;

/**
 * 告警通知服务
 * 支持多渠道通知：日志、Webhook（钉钉预留）
 */
@Service
public class AlertNotificationService {
    
    /**
     * 发送告警通知
     */
    public void send(AlertEvent event, AlertRule rule) {
        String channelsJson = rule.getChannelsJson();
        if (channelsJson == null || channelsJson.isEmpty()) {
            channelsJson = "[\"log\"]";
        }
        
        JSONArray channels;
        try {
            channels = JSON.parseArray(channelsJson);
        } catch (Exception e) {
            channels = new JSONArray();
            channels.add("log");
        }
        
        for (int i = 0; i < channels.size(); i++) {
            String channel = channels.getString(i);
            sendByChannel(channel, event);
        }
    }
    
    /**
     * 按渠道发送通知
     */
    private void sendByChannel(String channel, AlertEvent event) {
        switch (channel.toLowerCase()) {
            case "log":
                sendLog(event);
                break;
            case "webhook":
                sendWebhook(event);
                break;
            case "dingtalk":
                sendDingtalk(event);
                break;
            default:
                sendLog(event);
                break;
        }
    }
    
    /**
     * 日志通知（默认）
     */
    private void sendLog(AlertEvent event) {
        System.out.println("[ALERT-" + event.getSeverity() + "] " + event.getMessage());
    }
    
    /**
     * Webhook 通知（预留实现）
     */
    private void sendWebhook(AlertEvent event) {
        // TODO: 调用配置的 Webhook URL
        System.out.println("[WEBHOOK] " + JSON.toJSONString(event));
    }
    
    /**
     * 钉钉通知（预留实现）
     */
    private void sendDingtalk(AlertEvent event) {
        // TODO: 调用钉钉机器人 API
        System.out.println("[DINGTALK] " + event.getMessage());
    }
}
