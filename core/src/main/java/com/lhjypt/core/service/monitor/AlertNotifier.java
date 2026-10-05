package com.lhjypt.core.service.monitor;

import com.lhjypt.core.model.monitor.AlertEvent;

/**
 * 告警通知器：把已落库的告警事件投递到外部渠道。
 *
 * <p>实现必须自行吞掉发送异常（失败只记日志），不得影响告警评估主流程。</p>
 */
public interface AlertNotifier {

    /**
     * 发送告警通知。
     *
     * @param event 新生成的告警事件（已落库）
     */
    void notify(AlertEvent event);
}
