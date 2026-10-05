package com.lhjypt.core.service.trade.gateway;

import com.lhjypt.core.mapper.AlertEventMapper;
import com.lhjypt.core.mapper.GatewayInfoMapper;
import com.lhjypt.core.model.gateway.GatewayInfo;
import com.lhjypt.core.model.gateway.GatewayStatus;
import com.lhjypt.core.model.monitor.AlertEvent;
import com.lhjypt.core.model.trade.CTPConfig;
import com.lhjypt.core.service.trade.TradingGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 网关实例注册和健康状态管理。
 */
@Service
public class GatewayRegistry {

    private static final Logger log = LoggerFactory.getLogger(GatewayRegistry.class);
    private static final int HEALTH_FAILURE_THRESHOLD = 3;

    private final Map<String, ManagedGateway> gateways = new ConcurrentHashMap<>();
    private final GatewayInfoMapper gatewayInfoMapper;
    private final AlertEventMapper alertEventMapper;
    private final LongSupplier ticker;

    @Autowired
    public GatewayRegistry(GatewayInfoMapper gatewayInfoMapper,
                           AlertEventMapper alertEventMapper) {
        this(gatewayInfoMapper, alertEventMapper, System::nanoTime);
    }

    GatewayRegistry(GatewayInfoMapper gatewayInfoMapper,
                    AlertEventMapper alertEventMapper,
                    LongSupplier ticker) {
        this.gatewayInfoMapper = gatewayInfoMapper;
        this.alertEventMapper = alertEventMapper;
        this.ticker = ticker;
    }

    public ManagedGateway register(GatewayInfo info, TradingGateway gateway) {
        ManagedGateway managed = gateways.compute(info.getGatewayId(), (id, existing) -> {
            if (existing != null && existing.gateway() == gateway) {
                return existing;
            }
            WindowedCircuitBreaker breaker = new WindowedCircuitBreaker(
                    5_000_000_000L, 30_000_000_000L, 4, 0.5, ticker);
            return new ManagedGateway(info, gateway, breaker);
        });
        log.info("网关已注册: id={} type={} status={}",
                info.getGatewayId(), info.getGatewayType(), managed.status());
        return managed;
    }

    public ManagedGateway get(String gatewayId) {
        return gateways.get(gatewayId);
    }

    public List<ManagedGateway> list() {
        List<ManagedGateway> result = new ArrayList<>(gateways.values());
        result.sort(Comparator
                .comparingInt((ManagedGateway gateway) ->
                        gateway.info().getPriority() == null
                                ? Integer.MAX_VALUE : gateway.info().getPriority())
                .thenComparing(gateway -> gateway.info().getGatewayId()));
        return result;
    }

    public void connect(String gatewayId, CTPConfig config) {
        ManagedGateway managed = require(gatewayId);
        managed.status(GatewayStatus.CONNECTING);
        managed.openSession();
        try {
            managed.gateway().connect(config);
            managed.recordHealthSuccess();
            updateStatus(gatewayId, GatewayStatus.READY);
        } catch (Exception error) {
            managed.status(GatewayStatus.DOWN);
            updateStatus(gatewayId, GatewayStatus.DOWN);
            throw error;
        }
    }

    public void disconnect(String gatewayId) {
        ManagedGateway managed = require(gatewayId);
        managed.gateway().disconnect();
        managed.status(GatewayStatus.DOWN);
        updateStatus(gatewayId, GatewayStatus.DOWN);
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 30_000)
    public void scheduledHealthCheck() {
        try {
            healthCheckAll();
        } catch (Exception error) {
            log.warn("网关健康巡检失败: {}", error.getMessage());
        }
    }

    public int healthCheckAll() {
        int downCount = 0;
        for (ManagedGateway managed : list()) {
            if (managed.status() == GatewayStatus.DOWN) {
                continue;
            }
            boolean healthy = managed.gateway().isConnected()
                    && managed.gateway().isAuthenticated();
            if (healthy) {
                managed.recordHealthSuccess();
                updateStatus(managed.info().getGatewayId(), GatewayStatus.READY);
                continue;
            }
            int failures = managed.recordHealthFailure();
            if (failures >= HEALTH_FAILURE_THRESHOLD) {
                managed.status(GatewayStatus.DOWN);
                downCount++;
                updateStatus(managed.info().getGatewayId(), GatewayStatus.DOWN);
                raiseGatewayDownAlert(managed, failures);
            } else {
                managed.status(GatewayStatus.DEGRADED);
                updateStatus(managed.info().getGatewayId(), GatewayStatus.DEGRADED);
            }
        }
        return downCount;
    }

    private ManagedGateway require(String gatewayId) {
        ManagedGateway managed = gateways.get(gatewayId);
        if (managed == null) {
            throw new IllegalArgumentException("网关未注册: " + gatewayId);
        }
        return managed;
    }

    private void updateStatus(String gatewayId, GatewayStatus status) {
        try {
            gatewayInfoMapper.updateStatus(gatewayId, status.name(), LocalDateTime.now());
        } catch (Exception error) {
            log.debug("网关状态持久化失败: {} {}", gatewayId, error.getMessage());
        }
    }

    private void raiseGatewayDownAlert(ManagedGateway managed, int failures) {
        try {
            AlertEvent event = new AlertEvent();
            event.setEventId("GW-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            event.setRuleId("GATEWAY_HEALTH");
            event.setMetricKey("gateway.status");
            event.setValue(managed.info().getGatewayId());
            event.setSeverity("WARN");
            event.setMessage("网关连续健康检查失败 " + failures + " 次，已标记 DOWN");
            event.setStatus("OPEN");
            event.setCreateTime(LocalDateTime.now());
            alertEventMapper.insert(event);
        } catch (Exception error) {
            log.debug("网关告警写入失败: {}", error.getMessage());
        }
    }
}
