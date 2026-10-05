package com.lhjypt.core.model.monitor;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 指标采样数据模型（ClickHouse）
 */
public class MetricSample {

    /** 采样时间 */
    private LocalDateTime ts;

    /** 指标名称 */
    private String metric;

    /** 指标值 */
    private Double value;

    /** 标签（JSON格式） */
    private String labels;

    public LocalDateTime getTs() {
        return ts;
    }

    public void setTs(LocalDateTime ts) {
        this.ts = ts;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public String getLabels() {
        return labels;
    }

    public void setLabels(String labels) {
        this.labels = labels;
    }

    @Override
    public String toString() {
        return "MetricSample{ts=" + ts + ", metric='" + metric + "', value=" + value + ", labels='" + labels + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetricSample that = (MetricSample) o;
        return Objects.equals(ts, that.ts)
                && Objects.equals(metric, that.metric)
                && Objects.equals(value, that.value)
                && Objects.equals(labels, that.labels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ts, metric, value, labels);
    }
}
