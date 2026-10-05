package com.lhjypt.core.service.trade.gateway;

import com.alibaba.fastjson2.JSON;
import com.lhjypt.core.common.exception.GatewayUnavailableException;
import com.lhjypt.core.model.gateway.GatewayInfo;
import com.lhjypt.core.model.gateway.GatewayRouteDecision;
import com.lhjypt.core.model.gateway.GatewayRouteRule;
import com.lhjypt.core.model.gateway.GatewaySubmission;
import com.lhjypt.core.model.trade.AccountInfo;
import com.lhjypt.core.model.trade.OrderReport;
import com.lhjypt.core.model.trade.PositionDetail;
import com.lhjypt.core.model.trade.TradeReport;
import com.lhjypt.core.model.trade.TradeRequest;
import com.lhjypt.core.service.trade.TradingGateway;
import com.lhjypt.core.service.trade.impl.MockCTPGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 多网关路由、主备切换和熔断入口。
 */
@Service
public class GatewayRouter {

    private static final Logger log = LoggerFactory.getLogger(GatewayRouter.class);
    public static final String SWITCH_TOPIC = "topic.gateway-switch";

    private final GatewayRegistry registry;
    private final GatewayRouteService routeService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final List<TradingGateway.OrderReportListener> orderListeners =
            new ArrayList<>();
    private final List<TradingGateway.TradeReportListener> tradeListeners =
            new ArrayList<>();

    @Value("${lhjypt.gateway.submit-timeout-ms:2000}")
    private long submitTimeoutMillis;

    public GatewayRouter(GatewayRegistry registry,
                         GatewayRouteService routeService,
                         KafkaTemplate<String, String> kafkaTemplate) {
        this.registry = registry;
        this.routeService = routeService;
        this.kafkaTemplate = kafkaTemplate;
    }

    public synchronized ManagedGateway register(GatewayInfo info, TradingGateway gateway) {
        ManagedGateway managed = registry.register(info, gateway);
        attachListeners(managed);
        return managed;
    }

    public synchronized void registerReportListeners(
            TradingGateway.OrderReportListener orderListener,
            TradingGateway.TradeReportListener tradeListener) {
        if (orderListener != null) {
            orderListeners.add(orderListener);
        }
        if (tradeListener != null) {
            tradeListeners.add(tradeListener);
        }
        for (ManagedGateway managed : registry.list()) {
            attachListeners(managed);
        }
    }

    public GatewayRouteDecision chooseRoute(String accountId, String symbol,
                                            String orderType) {
        List<GatewayRouteRule> matched = routeService.enabledRules().stream()
                .filter(rule -> matches(rule, accountId, symbol, orderType))
                .sorted(Comparator
                        .comparingInt((GatewayRouteRule rule) ->
                                rule.getPriority() == null ? 100 : rule.getPriority())
                        .thenComparing(Comparator.comparingInt(
                                GatewayRouter::specificity).reversed()))
                .toList();

        for (GatewayRouteRule rule : matched) {
            ManagedGateway primary = registry.get(rule.getGatewayId());
            ManagedGateway fallback = registry.get(rule.getFallbackId());
            if (primary != null && primary.canRoute()) {
                return new GatewayRouteDecision(
                        primary.info().getGatewayId(),
                        primary.info().getGatewayId(),
                        availableId(fallback),
                        rule.getRuleId(),
                        "matched");
            }
            if (fallback != null && fallback.canRoute()) {
                return new GatewayRouteDecision(
                        rule.getGatewayId(),
                        rule.getFallbackId(),
                        null,
                        rule.getRuleId(),
                        "primary-unavailable");
            }
        }

        List<ManagedGateway> available = registry.list().stream()
                .filter(ManagedGateway::canRoute)
                .toList();
        if (available.isEmpty()) {
            throw new GatewayUnavailableException("当前无可用交易通道");
        }
        ManagedGateway primary = available.get(0);
        ManagedGateway fallback = available.size() > 1 ? available.get(1) : null;
        return new GatewayRouteDecision(
                primary.info().getGatewayId(),
                primary.info().getGatewayId(),
                availableId(fallback),
                null,
                matched.isEmpty() ? "default-priority" : "rules-unavailable");
    }

    public GatewaySubmission submitOrder(TradeRequest request) {
        GatewayRouteDecision decision = chooseRoute(
                request.getAccountId(), request.getSymbol(), request.getOrderType());
        ManagedGateway primary = requireManaged(decision.gatewayId());
        boolean switchedAtSelection = !Objects.equals(
                decision.primaryGatewayId(), decision.gatewayId());

        try {
            String orderRef = submitWithTimeout(primary, request);
            primary.recordSuccess();
            if (switchedAtSelection) {
                publishSwitch(request, decision.primaryGatewayId(),
                        decision.gatewayId(), decision.reason());
            }
            return new GatewaySubmission(
                    decision.gatewayId(), orderRef, switchedAtSelection,
                    switchedAtSelection ? decision.primaryGatewayId() : null);
        } catch (Exception primaryError) {
            primary.recordFailure(primaryError.getMessage());
            ManagedGateway fallback = registry.get(decision.fallbackId());
            if (fallback == null || fallback == primary || !fallback.allowRequest()) {
                throw new GatewayUnavailableException(
                        "主网关报单失败且无可用备用通道: " + primaryError.getMessage());
            }
            publishSwitch(request, primary.info().getGatewayId(),
                    fallback.info().getGatewayId(), "primary-submit-failed");
            try {
                String orderRef = submitWithTimeout(fallback, request);
                fallback.recordSuccess();
                return new GatewaySubmission(
                        fallback.info().getGatewayId(), orderRef,
                        true, primary.info().getGatewayId());
            } catch (Exception fallbackError) {
                fallback.recordFailure(fallbackError.getMessage());
                throw new GatewayUnavailableException(
                        "主备网关均报单失败: " + fallbackError.getMessage());
            }
        }
    }

    public boolean cancelOrder(String gatewayId, String exchangeOrderId, String symbol) {
        ManagedGateway managed = requireManaged(gatewayId);
        if (!managed.allowRequest()) {
            throw new GatewayUnavailableException("网关熔断或不可用: " + gatewayId);
        }
        try {
            boolean result = managed.gateway().cancelOrder(exchangeOrderId, symbol);
            managed.recordSuccess();
            return result;
        } catch (Exception error) {
            managed.recordFailure(error.getMessage());
            throw error;
        }
    }

    public OrderReport queryOrder(String gatewayId, String exchangeOrderId) {
        ManagedGateway managed = requireManaged(gatewayId);
        return managed.gateway().queryOrder(exchangeOrderId);
    }

    public List<TradeReport> queryTrades(String gatewayId) {
        return requireManaged(gatewayId).gateway().queryTrades();
    }

    public List<TradeReport> queryTradesAll() {
        List<TradeReport> result = new ArrayList<>();
        for (ManagedGateway managed : registry.list()) {
            if (!managed.canRoute()) {
                continue;
            }
            try {
                result.addAll(managed.gateway().queryTrades());
            } catch (Exception error) {
                log.warn("查询网关成交失败: {} {}", managed.info().getGatewayId(),
                        error.getMessage());
            }
        }
        return result;
    }

    public AccountInfo queryAccount(String gatewayId) {
        ManagedGateway managed = gatewayId == null || gatewayId.isBlank()
                ? firstReady()
                : requireManaged(gatewayId);
        return managed.gateway().queryAccount();
    }

    public List<PositionDetail> queryPositionsAll() {
        List<PositionDetail> result = new ArrayList<>();
        for (ManagedGateway managed : registry.list()) {
            if (!managed.canRoute()) {
                continue;
            }
            try {
                result.addAll(managed.gateway().queryPositions());
            } catch (Exception error) {
                log.warn("查询网关持仓失败: {} {}", managed.info().getGatewayId(),
                        error.getMessage());
            }
        }
        return result;
    }

    public List<Map<String, Object>> listGateways() {
        return registry.list().stream().map(managed -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("gatewayId", managed.info().getGatewayId());
            item.put("type", managed.info().getGatewayType());
            item.put("name", managed.info().getName());
            item.put("priority", managed.info().getPriority());
            item.put("status", managed.status().name());
            item.put("connected", managed.gateway().isConnected());
            item.put("authenticated", managed.gateway().isAuthenticated());
            item.put("breaker", managed.breaker().snapshot().state().name());
            item.put("failureRate", managed.breaker().snapshot().failureRate());
            item.put("lastHealthAt", managed.lastHealthAt());
            item.put("lastError", managed.lastError());
            if (managed.gateway() instanceof MockCTPGateway mock) {
                item.put("orderListenerCount", mock.orderListenerCount());
                item.put("tradeListenerCount", mock.tradeListenerCount());
            }
            return item;
        }).toList();
    }

    public boolean isAnyConnected() {
        return registry.list().stream()
                .anyMatch(managed -> managed.gateway().isConnected());
    }

    public boolean isAnyAuthenticated() {
        return registry.list().stream()
                .anyMatch(managed -> managed.gateway().isAuthenticated());
    }

    public Map<String, Object> health(String gatewayId) {
        ManagedGateway managed = requireManaged(gatewayId);
        WindowedCircuitBreaker.Snapshot snapshot = managed.breaker().snapshot();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gatewayId", gatewayId);
        result.put("status", managed.status().name());
        result.put("connected", managed.gateway().isConnected());
        result.put("authenticated", managed.gateway().isAuthenticated());
        result.put("consecutiveHealthFailures", managed.consecutiveHealthFailures());
        result.put("lastHealthAt", managed.lastHealthAt());
        result.put("breakerState", snapshot.state().name());
        result.put("windowCalls", snapshot.total());
        result.put("windowFailures", snapshot.failures());
        result.put("windowFailureRate", snapshot.failureRate());
        if (managed.gateway() instanceof MockCTPGateway mock) {
            result.put("orderListenerCount", mock.orderListenerCount());
            result.put("tradeListenerCount", mock.tradeListenerCount());
        }
        return result;
    }

    public GatewayRouteRule switchAccount(String accountId, String gatewayId,
                                          String fallbackId, String operator) {
        requireManaged(gatewayId);
        if (fallbackId != null && !fallbackId.isBlank()) {
            requireManaged(fallbackId);
        }
        GatewayRouteRule rule = routeService.switchAccount(
                accountId, gatewayId, fallbackId, operator);
        log.info("账户流量手动切换: account={} gateway={} fallback={} operator={}",
                accountId, gatewayId, fallbackId, operator);
        return rule;
    }

    public GatewayRouteService routeService() {
        return routeService;
    }

    public GatewayRegistry registry() {
        return registry;
    }

    private String submitWithTimeout(ManagedGateway managed, TradeRequest request)
            throws Exception {
        if (!managed.allowRequest()) {
            throw new GatewayUnavailableException("网关不可用: "
                    + managed.info().getGatewayId());
        }
        long timeout = Math.max(100, submitTimeoutMillis);
        CompletableFuture<String> future = CompletableFuture.supplyAsync(
                () -> managed.gateway().submitOrder(request));
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (Exception error) {
            future.cancel(true);
            throw error;
        }
    }

    private ManagedGateway requireManaged(String gatewayId) {
        ManagedGateway managed = registry.get(gatewayId);
        if (managed == null) {
            throw new IllegalArgumentException("网关未注册: " + gatewayId);
        }
        return managed;
    }

    private ManagedGateway firstReady() {
        return registry.list().stream()
                .filter(ManagedGateway::canRoute)
                .findFirst()
                .orElseThrow(() -> new GatewayUnavailableException("当前无可用交易通道"));
    }

    private synchronized void attachListeners(ManagedGateway managed) {
        String gatewayId = managed.info().getGatewayId();
        for (TradingGateway.OrderReportListener listener : orderListeners) {
            managed.gateway().addOrderReportListener(report -> {
                report.setGatewayId(gatewayId);
                report.setGatewaySessionId(managed.sessionId());
                listener.onOrderReport(report);
            });
        }
        for (TradingGateway.TradeReportListener listener : tradeListeners) {
            managed.gateway().addTradeReportListener(report -> {
                report.setGatewayId(gatewayId);
                report.setGatewaySessionId(managed.sessionId());
                listener.onTradeReport(report);
            });
        }
    }

    private static boolean matches(GatewayRouteRule rule, String accountId,
                                   String symbol, String orderType) {
        if (rule.getAccountId() != null && !rule.getAccountId().isBlank()
                && !rule.getAccountId().equals(accountId)) {
            return false;
        }
        if (rule.getOrderType() != null && !rule.getOrderType().isBlank()
                && !rule.getOrderType().equalsIgnoreCase(orderType)) {
            return false;
        }
        if (rule.getSymbolRegex() == null || rule.getSymbolRegex().isBlank()) {
            return true;
        }
        try {
            return Pattern.compile(rule.getSymbolRegex()).matcher(symbol).matches();
        } catch (PatternSyntaxException error) {
            log.warn("路由规则标的正则非法: {} {}", rule.getRuleId(), rule.getSymbolRegex());
            return false;
        }
    }

    private static int specificity(GatewayRouteRule rule) {
        int score = 0;
        if (rule.getAccountId() != null && !rule.getAccountId().isBlank()) {
            score++;
        }
        if (rule.getSymbolRegex() != null && !rule.getSymbolRegex().isBlank()) {
            score++;
        }
        if (rule.getOrderType() != null && !rule.getOrderType().isBlank()) {
            score++;
        }
        return score;
    }

    private static String availableId(ManagedGateway managed) {
        return managed != null && managed.canRoute()
                ? managed.info().getGatewayId() : null;
    }

    private void publishSwitch(TradeRequest request, String from,
                               String to, String reason) {
        if (kafkaTemplate == null) {
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("accountId", request.getAccountId());
        event.put("strategyId", request.getStrategyId());
        event.put("symbol", request.getSymbol());
        event.put("fromGateway", from);
        event.put("toGateway", to);
        event.put("reason", reason);
        event.put("switchTime", LocalDateTime.now().toString());
        try {
            kafkaTemplate.send(SWITCH_TOPIC,
                    request.getAccountId(), JSON.toJSONString(event));
        } catch (Exception error) {
            log.warn("网关切换事件发送失败: {} -> {} {}", from, to, error.getMessage());
        }
    }
}
