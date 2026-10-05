package com.lhjypt.core.mapper;

import com.lhjypt.core.model.monitor.MetricSample;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 指标采样数据访问层（ClickHouse）
 */
@Repository
public class MetricSampleMapper {
    
    private final JdbcTemplate clickHouseJdbcTemplate;
    
    public MetricSampleMapper(@Qualifier("clickHouseJdbcTemplate") JdbcTemplate clickHouseJdbcTemplate) {
        this.clickHouseJdbcTemplate = clickHouseJdbcTemplate;
    }
    
    /**
     * 插入指标采样数据
     */
    public int insert(MetricSample sample) {
        String sql = "INSERT INTO lhjypt.metric_sample (ts, metric, value, labels) VALUES (?, ?, ?, ?)";
        return clickHouseJdbcTemplate.update(sql,
                Timestamp.valueOf(sample.getTs()),
                sample.getMetric(),
                sample.getValue(),
                sample.getLabels() != null ? sample.getLabels() : "{}");
    }
    
    /**
     * 查询指标历史数据
     */
    public List<MetricSample> queryHistory(String metric, LocalDateTime from, LocalDateTime to) {
        String sql = "SELECT ts, metric, value, labels FROM lhjypt.metric_sample " +
                "WHERE metric = ? AND ts >= ? AND ts <= ? ORDER BY ts DESC";
        return clickHouseJdbcTemplate.query(sql, (rs, rowNum) -> {
            MetricSample sample = new MetricSample();
            sample.setTs(rs.getTimestamp("ts").toLocalDateTime());
            sample.setMetric(rs.getString("metric"));
            sample.setValue(rs.getDouble("value"));
            sample.setLabels(rs.getString("labels"));
            return sample;
        }, metric, Timestamp.valueOf(from), Timestamp.valueOf(to));
    }
    
    /**
     * 查询最新指标值
     */
    public MetricSample queryLatest(String metric) {
        String sql = "SELECT ts, metric, value, labels FROM lhjypt.metric_sample " +
                "WHERE metric = ? ORDER BY ts DESC LIMIT 1";
        List<MetricSample> results = clickHouseJdbcTemplate.query(sql, (rs, rowNum) -> {
            MetricSample sample = new MetricSample();
            sample.setTs(rs.getTimestamp("ts").toLocalDateTime());
            sample.setMetric(rs.getString("metric"));
            sample.setValue(rs.getDouble("value"));
            sample.setLabels(rs.getString("labels"));
            return sample;
        }, metric);
        return results.isEmpty() ? null : results.get(0);
    }
    
    /**
     * 计算窗口内平均值
     */
    public Double queryWindowAvg(String metric, LocalDateTime from, LocalDateTime to) {
        String sql = "SELECT avg(value) as avg_value FROM lhjypt.metric_sample " +
                "WHERE metric = ? AND ts >= ? AND ts <= ?";
        Double result = clickHouseJdbcTemplate.queryForObject(sql, Double.class, 
                metric, Timestamp.valueOf(from), Timestamp.valueOf(to));
        return result;
    }
}
