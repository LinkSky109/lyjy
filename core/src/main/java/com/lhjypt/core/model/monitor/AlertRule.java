package com.lhjypt.core.model.monitor;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 告警规则
 */
@TableName("alert_rule")
public class AlertRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则ID */
    private String ruleId;

    /** 规则名称 */
    private String name;

    /** 指标键 */
    private String metricKey;

    /** 条件: GT/LT/EQ/NEQ */
    private String condition;

    /** 阈值 */
    private Double threshold;

    /** 评估窗口(秒) */
    private Integer windowSec;

    /** 严重级别: INFO/WARN/CRITICAL */
    private String severity;

    /** 通知渠道JSON数组 ["log","email","dingtalk"] */
    private String channelsJson;

    /** 是否启用 */
    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getName() {
        return name;
    }

    public String getMetricKey() {
        return metricKey;
    }

    public String getCondition() {
        return condition;
    }

    public Double getThreshold() {
        return threshold;
    }

    public Integer getWindowSec() {
        return windowSec;
    }

    public String getSeverity() {
        return severity;
    }

    public String getChannelsJson() {
        return channelsJson;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setMetricKey(String metricKey) {
        this.metricKey = metricKey;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public void setThreshold(Double threshold) {
        this.threshold = threshold;
    }

    public void setWindowSec(Integer windowSec) {
        this.windowSec = windowSec;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public void setChannelsJson(String channelsJson) {
        this.channelsJson = channelsJson;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    @Override
    public String toString() {
        return "AlertRule{id=" + id + ", ruleId=" + ruleId + ", name=" + name + ", metricKey=" + metricKey + ", condition=" + condition + ", threshold=" + threshold + ", windowSec=" + windowSec + ", severity=" + severity + ", channelsJson=" + channelsJson + ", enabled=" + enabled + ", createTime=" + createTime + ", updateTime=" + updateTime + ", deleted=" + deleted + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AlertRule that = (AlertRule) o;
        return Objects.equals(id, that.id)
                && Objects.equals(ruleId, that.ruleId)
                && Objects.equals(name, that.name)
                && Objects.equals(metricKey, that.metricKey)
                && Objects.equals(condition, that.condition)
                && Objects.equals(threshold, that.threshold)
                && Objects.equals(windowSec, that.windowSec)
                && Objects.equals(severity, that.severity)
                && Objects.equals(channelsJson, that.channelsJson)
                && Objects.equals(enabled, that.enabled)
                && Objects.equals(createTime, that.createTime)
                && Objects.equals(updateTime, that.updateTime)
                && Objects.equals(deleted, that.deleted);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, ruleId, name, metricKey, condition, threshold, windowSec, severity, channelsJson, enabled, createTime, updateTime, deleted);
    }
}
