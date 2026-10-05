package com.lhjypt.core.service.trade.gateway;

import com.lhjypt.core.model.gateway.GatewayInfo;
import com.lhjypt.core.model.gateway.GatewayStatus;
import com.lhjypt.core.service.trade.TradingGateway;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 路由器内部管理的网关实例。
 */
public class ManagedGateway {

    private final GatewayInfo info;
    private final TradingGateway gateway;
    private final WindowedCircuitBreaker breaker;

    private volatile GatewayStatus status;
    private volatile int consecutiveHealthFailures;
    private volatile LocalDateTime lastHealthAt;
    private volatile String lastError;
    private volatile String sessionId;

    public ManagedGateway(GatewayInfo info, TradingGateway gateway,
                          WindowedCircuitBreaker breaker) {
        this.info = info;
        this.gateway = gateway;
        this.breaker = breaker;
        this.status = GatewayStatus.valueOf(
                info.getStatus() == null ? "DOWN" : info.getStatus());
    }

    public boolean canRoute() {
        return status == GatewayStatus.READY
                && gateway.isAuthenticated()
                && breaker.snapshot().state() != WindowedCircuitBreaker.State.OPEN;
    }

    public boolean allowRequest() {
        return status == GatewayStatus.READY
                && gateway.isAuthenticated()
                && breaker.allowRequest();
    }

    public void recordSuccess() {
        breaker.recordSuccess();
    }

    public void recordFailure(String error) {
        breaker.recordFailure();
        this.lastError = error;
    }

    public GatewayInfo info() {
        return info;
    }

    public TradingGateway gateway() {
        return gateway;
    }

    public WindowedCircuitBreaker breaker() {
        return breaker;
    }

    public GatewayStatus status() {
        return status;
    }

    public void status(GatewayStatus status) {
        this.status = status;
    }

    public int consecutiveHealthFailures() {
        return consecutiveHealthFailures;
    }

    public void recordHealthSuccess() {
        consecutiveHealthFailures = 0;
        lastHealthAt = LocalDateTime.now();
        status = GatewayStatus.READY;
    }

    public int recordHealthFailure() {
        consecutiveHealthFailures++;
        lastHealthAt = LocalDateTime.now();
        return consecutiveHealthFailures;
    }

    public LocalDateTime lastHealthAt() {
        return lastHealthAt;
    }

    public String lastError() {
        return lastError;
    }

    public String sessionId() {
        return sessionId;
    }

    public void openSession() {
        this.sessionId = info.getGatewayId() + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
