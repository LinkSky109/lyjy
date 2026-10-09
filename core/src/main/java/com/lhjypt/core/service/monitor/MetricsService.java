package com.lhjypt.core.service.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 业务指标记录（P3-004）：把关键业务动作暴露到 Micrometer/Prometheus。
 * HTTP/JVM/连接池等基础指标由 Spring Boot 自动采集，此处补充业务计数。
 */
@Service
public class MetricsService {

    private final MeterRegistry registry;
    private final AtomicReference<Double> capacityLoad = new AtomicReference<>(0.0);

    public MetricsService(MeterRegistry registry) {
        this.registry = registry;
        // 容量水位 gauge（值由 CapacityService 写入）
        registry.gauge("lhjypt_capacity_load_pct", capacityLoad, AtomicReference::get);
    }

    /** 下单提交（含被风控拒绝前的提交尝试）。 */
    public void orderSubmitted(String mode) {
        counter("lhjypt_orders_submitted_total", "mode", mode == null ? "UNKNOWN" : mode).increment();
    }

    /** 回测执行（状态标签 + 耗时）。 */
    public void backtestRun(String status, long durationMs) {
        counter("lhjypt_backtest_runs_total", "status", status == null ? "UNKNOWN" : status).increment();
        Timer.builder("lhjypt_backtest_duration")
                .tag("status", status == null ? "UNKNOWN" : status)
                .register(registry)
                .record(Math.max(0, durationMs), TimeUnit.MILLISECONDS);
    }

    /** 真实行情刷新（新增 K 线条数）。 */
    public void marketRefresh(int inserted) {
        counter("lhjypt_market_refresh_total", "result", "ok").increment();
        counter("lhjypt_market_refresh_inserted_total", "result", "ok").increment(Math.max(0, inserted));
    }

    /** 真实行情刷新失败（网络/源不可用）。 */
    public void marketRefreshFailure() {
        counter("lhjypt_market_refresh_total", "result", "error").increment();
    }

    /** 风控告警/熔断触发（kind: strategy|portfolio|alert）。 */
    public void circuitTrip(String kind) {
        counter("lhjypt_circuit_trips_total", "kind", kind == null ? "unknown" : kind).increment();
    }

    /** 容量水位（%）。 */
    public void capacityLoad(double loadPct) {
        capacityLoad.set(loadPct);
    }

    private Counter counter(String name, String tagKey, String tagValue) {
        return Counter.builder(name).tag(tagKey, tagValue).register(registry);
    }
}