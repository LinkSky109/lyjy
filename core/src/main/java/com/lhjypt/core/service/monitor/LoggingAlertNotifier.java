package com.lhjypt.core.service.monitor;

import com.lhjypt.core.model.monitor.AlertEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 日志告警通知器（默认实现）。
 *
 * <p>外部通知关闭（{@code lhjypt.monitoring.notify.enabled=false} 或未配置）时装配，
 * 只把告警打到日志，实现“日志降级”。</p>
 */
@Component
@ConditionalOnProperty(name = "lhjypt.monitoring.notify.enabled",
        havingValue = "false", matchIfMissing = true)
public class LoggingAlertNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingAlertNotifier.class);

    @Override
    public void notify(AlertEvent event) {
        log.info("[ALERT-{}] eventId={} metricKey={} value={} status={} message={}",
                event.getSeverity(), event.getEventId(), event.getMetricKey(),
                event.getValue(), event.getStatus(), event.getMessage());
    }
}
