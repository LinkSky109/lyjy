package com.lhjypt.core.service.strategy;

import com.lhjypt.core.common.auth.UserContext;
import com.lhjypt.core.config.DistributedLock;
import com.lhjypt.core.config.StrategyWatchdogProperties;
import com.lhjypt.core.model.strategy.WatchdogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 策略自动值守（OBS-010）：把信号策略变成平台托管的“挂机”运行。
 *
 * <p>每 {@code scanIntervalMs} 扫描一次已启用的值守列表：
 * <ul>
 *   <li>运行中 → 记录状态，跳过；</li>
 *   <li>未运行且已过冷却（按条目 checkIntervalMs）→ 以系统上下文拉起
 *       （环境绑定/密钥等校验错误记入 lastError 并冷却，避免反复拉起失败）。</li>
 * </ul>
 * 值守列表由 API 动态管理（内存态，重启后需重新启用；文档给出配置持久化方式）。
 */
@Service
public class StrategyWatchdogService {

    private static final Logger log = LoggerFactory.getLogger(StrategyWatchdogService.class);

    private static final String SYS_USER = "system";
    private static final UserContext SYSTEM = UserContext.of(SYS_USER,
            UserContext.ROLE_ADMIN, null);

    private final StrategySecurityService strategySecurityService;
    private final StrategyWatchdogProperties properties;
    private final DistributedLock distributedLock;
    private final Map<String, WatchdogEntry> watchdogs = new ConcurrentHashMap<>();

    public StrategyWatchdogService(StrategySecurityService strategySecurityService,
                                   StrategyWatchdogProperties properties,
                                   DistributedLock distributedLock) {
        this.strategySecurityService = strategySecurityService;
        this.properties = properties;
        this.distributedLock = distributedLock;
    }

    /** 启用/停用某策略值守；校验归属（非 ADMIN 只能设自己策略）。 */
    public WatchdogEntry setWatchdog(String strategyId, boolean enabled,
                                     Long checkIntervalMs, UserContext context) {
        strategySecurityService.requireStrategyOwnerOrAdmin(strategyId, context);
        WatchdogEntry entry = watchdogs.computeIfAbsent(strategyId,
                id -> new WatchdogEntry(id, enabled, checkIntervalMs));
        entry.setEnabled(enabled);
        if (checkIntervalMs != null && checkIntervalMs > 0) {
            entry.setCheckIntervalMs(checkIntervalMs);
        }
        log.info("策略值守 {} -> {}", strategyId, enabled ? "ON" : "OFF");
        return entry;
    }

    public List<WatchdogEntry> list() {
        List<WatchdogEntry> result = new ArrayList<>(watchdogs.values());
        result.sort(Comparator.comparing(WatchdogEntry::getStrategyId));
        return result;
    }

    @Scheduled(fixedRateString = "${lhjypt.strategy.watchdog.scan-interval-ms:30000}")
    public void scan() {
        if (!properties.isEnabled()) {
            return;
        }
        distributedLock.execute("strategy.watchdog", () -> {
            for (WatchdogEntry entry : watchdogs.values()) {
                if (!entry.getEnabled()) {
                    continue;
                }
                try {
                    guardian(entry);
                } catch (Exception error) {
                    entry.setLastError(String.valueOf(error.getMessage()));
                    entry.markAttempt();
                    log.warn("策略值守引擎异常 {}: {}",
                            entry.getStrategyId(), error.getMessage());
                }
            }
        });
    }

    private void guardian(WatchdogEntry entry) {
        Map<String, Object> status = strategySecurityService.sandboxStatus(
                entry.getStrategyId(), SYSTEM);
        String current = String.valueOf(status.getOrDefault("status", "OFFLINE"));
        entry.setLastStatus(current);
        if (isRunning(current)) {
            entry.markAttempt(); // 运行中，重置冷却
            return;
        }
        if (entry.withinCooldown()) {
            return;
        }
        entry.markAttempt();
        try {
            Map<String, Object> runResult = strategySecurityService.run(
                    entry.getStrategyId(), SYSTEM, "watchdog");
            entry.setLastStatus(String.valueOf(runResult.getOrDefault("status", "UNKNOWN")));
            entry.setLastError(null);
            log.info("策略值守拉起 {} -> {}", entry.getStrategyId(), entry.getLastStatus());
        } catch (Exception error) {
            entry.setLastError(error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage());
            log.warn("策略值守拉起失败 {}: {}", entry.getStrategyId(), entry.getLastError());
        }
    }

    static boolean isRunning(String status) {
        return "RUNNING".equalsIgnoreCase(status);
    }
}