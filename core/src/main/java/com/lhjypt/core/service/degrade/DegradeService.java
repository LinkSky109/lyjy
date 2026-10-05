package com.lhjypt.core.service.degrade;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lhjypt.core.mapper.AlertEventMapper;
import com.lhjypt.core.mapper.DegradeSwitchMapper;
import com.lhjypt.core.model.degrade.DegradeSwitch;
import com.lhjypt.core.model.monitor.AlertEvent;
import com.lhjypt.core.service.market.impl.MockMarketDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 性能降级预案（P1，TASK-P1-004）
 *
 * - 过载保护：订单入口令牌桶限流（默认容量/稳态 500 TPS，可配）
 * - 优雅降级：非核心功能开关（degrade_switch 表，10s 热更新扫描），核心链路（degrade.core.*）永不降级
 * - 行情源降级：degrade.market.backup ON → 行情源降频为快照级（5s）
 * - 熔断：轻量 SimpleCircuitBreaker 注册表（gateway/clickhouse/kafka），状态上报监控
 * - 每次开关变更写 alert_event 留痕 + 日志（操作需确认口令，由 Controller 层校验）
 */
@Service
public class DegradeService {

    private static final Logger log = LoggerFactory.getLogger(DegradeService.class);

    /** 允许降级的非核心开关白名单（核心 degrade.core.* 一律拒绝） */
    private static final Set<String> ALLOWED_KEYS = Set.of(
            "degrade.market.backup",
            "degrade.feature.var",
            "degrade.feature.report",
            "degrade.feature.attribution",
            "degrade.strategy.pause");

    private static final long NORMAL_TICK_MS = 500;
    private static final long BACKUP_TICK_MS = 5000;

    private final DegradeSwitchMapper switchMapper;
    private final AlertEventMapper alertEventMapper;
    private final MockMarketDataProvider marketProvider;
    private final LongSupplier nanos;

    /** 订单入口限流：容量=稳态 500 TPS */
    private final TokenBucket orderBucket;
    /** 熔断器注册表 */
    private final Map<String, SimpleCircuitBreaker> breakers = new ConcurrentHashMap<>();
    /** 开关内存态（10s 热更新） */
    private final Map<String, String> switchState = new ConcurrentHashMap<>();

    public DegradeService(DegradeSwitchMapper switchMapper,
                          AlertEventMapper alertEventMapper,
                          MockMarketDataProvider marketProvider,
                          LongSupplier nanos) {
        this.switchMapper = switchMapper;
        this.alertEventMapper = alertEventMapper;
        this.marketProvider = marketProvider;
        this.nanos = nanos;
        this.orderBucket = new TokenBucket(500, 500, nanos);
    }

    /** 注册默认开关与熔断器（幂等，启动时调用） */
    public void registerDefaults() {
        for (String key : ALLOWED_KEYS) {
            if (switchMapper.selectByKey(key) == null) {
                DegradeSwitch sw = new DegradeSwitch();
                sw.setSwitchKey(key);
                sw.setLabel(labelOf(key));
                sw.setLevel("OFF");
                sw.setOperator("system");
                sw.setReason("P1 初始化");
                sw.setCreateTime(LocalDateTime.now());
                sw.setUpdateTime(LocalDateTime.now());
                switchMapper.insert(sw);
            }
        }
        refreshFromTable();
        breakers.computeIfAbsent("gateway", k -> new SimpleCircuitBreaker(5, 60_000_000_000L, nanos));
        breakers.computeIfAbsent("clickhouse", k -> new SimpleCircuitBreaker(5, 60_000_000_000L, nanos));
        breakers.computeIfAbsent("kafka", k -> new SimpleCircuitBreaker(5, 30_000_000_000L, nanos));
    }

    private static String labelOf(String key) {
        return switch (key) {
            case "degrade.market.backup" -> "行情源备用降级（快照级 5s）";
            case "degrade.feature.var" -> "暂停 VaR 计算";
            case "degrade.feature.report" -> "暂停报表生成";
            case "degrade.feature.attribution" -> "暂停绩效归因";
            case "degrade.strategy.pause" -> "暂停低优先级策略";
            default -> key;
        };
    }

    /**
     * 设置开关（level: ON/OFF）。核心链路降级一律拒绝。
     */
    public DegradeSwitch setSwitch(String key, String level, String operator, String reason) {
        if (key != null && key.startsWith("degrade.core.")) {
            throw new IllegalArgumentException("核心链路（报单/撤单）永不降级: " + key);
        }
        if (!ALLOWED_KEYS.contains(key)) {
            throw new IllegalArgumentException("未知降级开关: " + key);
        }
        if (!"ON".equals(level) && !"OFF".equals(level)) {
            throw new IllegalArgumentException("level 只允许 ON/OFF: " + level);
        }

        DegradeSwitch sw = switchMapper.selectByKey(key);
        LocalDateTime now = LocalDateTime.now();
        if (sw == null) {
            sw = new DegradeSwitch();
            sw.setSwitchKey(key);
            sw.setLabel(labelOf(key));
            sw.setLevel(level);
            sw.setOperator(operator);
            sw.setReason(reason);
            sw.setCreateTime(now);
            sw.setUpdateTime(now);
            switchMapper.insert(sw);
        } else {
            sw.setLevel(level);
            sw.setOperator(operator);
            sw.setReason(reason);
            sw.setUpdateTime(now);
            switchMapper.updateById(sw);
        }
        applySideEffects(key, level);
        switchState.put(key, level);
        raiseEvent(key, level, operator, reason);
        log.info("降级开关变更: {}={} by {} ({})", key, level, operator, reason);
        return sw;
    }

    /** 开关副作用：行情源降频/恢复 */
    private void applySideEffects(String key, String level) {
        if ("degrade.market.backup".equals(key)) {
            if ("ON".equals(level)) {
                marketProvider.setTickIntervalMillis(BACKUP_TICK_MS);
            } else {
                marketProvider.setTickIntervalMillis(NORMAL_TICK_MS);
            }
        }
    }

    private void raiseEvent(String key, String level, String operator, String reason) {
        AlertEvent e = new AlertEvent();
        e.setEventId("EV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        e.setRuleId("DEGRADE");
        e.setMetricKey(key);
        e.setValue(level);
        e.setSeverity("ON".equals(level) ? "WARN" : "INFO");
        e.setMessage("降级开关 " + key + " → " + level
                + "（操作人 " + operator + "，原因：" + reason + "）");
        e.setStatus("OPEN");
        e.setCreateTime(LocalDateTime.now());
        alertEventMapper.insert(e);
    }

    /** 10s 热更新：从表刷回内存态并应用副作用（DB 为权威源） */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 10_000, initialDelay = 20_000)
    public void scheduledRefresh() {
        try {
            refreshFromTable();
        } catch (Exception ex) {
            log.warn("降级开关热更新失败: {}", ex.getMessage());
        }
    }

    public void refreshFromTable() {
        for (DegradeSwitch sw : switchMapper.selectList(new QueryWrapper<>())) {
            String prev = switchState.put(sw.getSwitchKey(), sw.getLevel());
            if (prev != null && !prev.equals(sw.getLevel())) {
                applySideEffects(sw.getSwitchKey(), sw.getLevel());
            }
        }
    }

    public boolean isOn(String key) {
        return "ON".equals(switchState.get(key));
    }

    /** 订单入口限流判定（核心链路，只限流不降级） */
    public boolean tryAcquireOrderPermit() {
        return orderBucket.tryAcquire();
    }

    /** 获取具名熔断器（供网关/CH/Kafka 调用方埋点） */
    public SimpleCircuitBreaker breaker(String name) {
        return breakers.computeIfAbsent(name,
                k -> new SimpleCircuitBreaker(5, 60_000_000_000L, nanos));
    }

    /** 容量水位（API 与监控用） */
    public Map<String, Object> capacityWaterLevel() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderTpsCapacity", orderBucket.capacity());
        m.put("orderTokensAvailable", Math.round(orderBucket.available()));
        List<String> active = new ArrayList<>();
        switchState.forEach((k, v) -> {
            if ("ON".equals(v)) active.add(k);
        });
        m.put("activeSwitches", active);
        Map<String, String> bs = new LinkedHashMap<>();
        breakers.forEach((k, v) -> bs.put(k, v.state().name()));
        m.put("breakers", bs);
        return m;
    }

    /** 全部开关状态 */
    public List<DegradeSwitch> listSwitches() {
        return switchMapper.selectList(new QueryWrapper<>());
    }
}
