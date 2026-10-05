package com.lhjypt.core.model.monitor;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 告警事件
 */
@TableName("alert_event")
public class AlertEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件ID */
    private String eventId;

    /** 关联规则ID */
    private String ruleId;

    /** 指标键 */
    private String metricKey;

    /** 触发值 */
    private String value;

    /** 严重级别 */
    private String severity;

    /** 告警消息 */
    private String message;

    /** 状态: OPEN/ACKED/CLOSED */
    private String status;

    /** 确认人 */
    private String ackBy;

    /** 确认时间 */
    private LocalDateTime ackTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getMetricKey() {
        return metricKey;
    }

    public String getValue() {
        return value;
    }

    public String getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public String getStatus() {
        return status;
    }

    public String getAckBy() {
        return ackBy;
    }

    public LocalDateTime getAckTime() {
        return ackTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public void setMetricKey(String metricKey) {
        this.metricKey = metricKey;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setAckBy(String ackBy) {
        this.ackBy = ackBy;
    }

    public void setAckTime(LocalDateTime ackTime) {
        this.ackTime = ackTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "AlertEvent{id=" + id + ", eventId=" + eventId + ", ruleId=" + ruleId + ", metricKey=" + metricKey + ", value=" + value + ", severity=" + severity + ", message=" + message + ", status=" + status + ", ackBy=" + ackBy + ", ackTime=" + ackTime + ", createTime=" + createTime + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AlertEvent that = (AlertEvent) o;
        return Objects.equals(id, that.id)
                && Objects.equals(eventId, that.eventId)
                && Objects.equals(ruleId, that.ruleId)
                && Objects.equals(metricKey, that.metricKey)
                && Objects.equals(value, that.value)
                && Objects.equals(severity, that.severity)
                && Objects.equals(message, that.message)
                && Objects.equals(status, that.status)
                && Objects.equals(ackBy, that.ackBy)
                && Objects.equals(ackTime, that.ackTime)
                && Objects.equals(createTime, that.createTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, eventId, ruleId, metricKey, value, severity, message, status, ackBy, ackTime, createTime);
    }
}
