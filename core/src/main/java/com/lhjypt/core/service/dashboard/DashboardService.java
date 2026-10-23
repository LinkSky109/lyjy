package com.lhjypt.core.service.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lhjypt.core.mapper.AlertEventMapper;
import com.lhjypt.core.mapper.FundAccountMapper;
import com.lhjypt.core.mapper.OrderMapper;
import com.lhjypt.core.model.FundAccount;
import com.lhjypt.core.model.Order;
import com.lhjypt.core.model.monitor.AlertEvent;
import com.lhjypt.core.service.market.MarketDataService;
import com.lhjypt.core.service.order.OrderStatus;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘只读聚合：状态、资金、盈亏、告警与委托计数。
 * 所有字段直接来自现有服务与表，不在前端编造聚合值。
 */
@Service
public class DashboardService {

    private final FundAccountMapper fundAccountMapper;
    private final OrderMapper orderMapper;
    private final AlertEventMapper alertEventMapper;
    private final MarketDataService marketDataService;
    private final HealthEndpoint healthEndpoint;

    public DashboardService(FundAccountMapper fundAccountMapper,
                            OrderMapper orderMapper,
                            AlertEventMapper alertEventMapper,
                            MarketDataService marketDataService,
                            HealthEndpoint healthEndpoint) {
        this.fundAccountMapper = fundAccountMapper;
        this.orderMapper = orderMapper;
        this.alertEventMapper = alertEventMapper;
        this.marketDataService = marketDataService;
        this.healthEndpoint = healthEndpoint;
    }

    public Map<String, Object> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", serviceStatus());
        result.put("market", marketStatus());
        result.put("components", components());
        result.put("equity", equity());
        result.put("pnl", pnl());
        result.put("alerts", alertCounts());
        result.put("orders", orderCounts());
        result.put("asOf", LocalDateTime.now());
        return result;
    }

    public Map<String, Object> components() {
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("service", safeAggregateStatus());
        components.put("postgres", healthStatus("db", "primaryDataSource"));
        components.put("clickhouse", healthStatus("db", "clickHouseDataSource"));
        components.put("redis", healthStatus("redis"));
        components.put("kafka", healthStatus("kafka"));
        return components;
    }

    private Map<String, Object> serviceStatus() {
        Map<String, Object> service = new LinkedHashMap<>();
        service.put("status", safeAggregateStatus());
        return service;
    }

    private Map<String, Object> marketStatus() {
        Map<String, Object> market = new LinkedHashMap<>();
        market.put("running", marketDataService.isRunning());
        market.put("subscribed", marketDataService.getSubscribedSymbols().size());
        return market;
    }

    private Map<String, Object> equity() {
        List<FundAccount> accounts = fundAccountMapper.selectList(
                new QueryWrapper<FundAccount>().eq("deleted", 0));
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal available = BigDecimal.ZERO;
        BigDecimal frozen = BigDecimal.ZERO;
        BigDecimal margin = BigDecimal.ZERO;
        for (FundAccount account : accounts) {
            total = total.add(nullSafe(account.getTotalAsset()));
            available = available.add(nullSafe(account.getAvailableCash()));
            frozen = frozen.add(nullSafe(account.getFrozenCash()));
            margin = margin.add(nullSafe(account.getMargin()));
        }
        Map<String, Object> equity = new LinkedHashMap<>();
        equity.put("total", total);
        equity.put("available", available);
        equity.put("frozen", frozen);
        equity.put("margin", margin);
        equity.put("marginRatio", ratio(margin, total));
        equity.put("accounts", accounts.size());
        return equity;
    }

    private Map<String, Object> pnl() {
        List<FundAccount> accounts = fundAccountMapper.selectList(
                new QueryWrapper<FundAccount>().eq("deleted", 0));
        BigDecimal unrealized = BigDecimal.ZERO;
        BigDecimal realized = BigDecimal.ZERO;
        for (FundAccount account : accounts) {
            unrealized = unrealized.add(nullSafe(account.getUnrealizedPnl()));
            realized = realized.add(nullSafe(account.getRealizedPnl()));
        }
        Map<String, Object> pnl = new LinkedHashMap<>();
        pnl.put("unrealized", unrealized);
        pnl.put("realized", realized);
        pnl.put("combined", unrealized.add(realized));
        pnl.put("basis", "CUMULATIVE");
        return pnl;
    }

    private Map<String, Object> alertCounts() {
        List<AlertEvent> open = alertEventMapper.selectByStatus("OPEN");
        long critical = open.stream()
                .filter(event -> "CRITICAL".equalsIgnoreCase(event.getSeverity()))
                .count();
        Map<String, Object> alerts = new LinkedHashMap<>();
        alerts.put("open", open.size());
        alerts.put("critical", critical);
        return alerts;
    }

    private Map<String, Object> orderCounts() {
        int active = orderMapper.selectActiveOrders().size();
        List<Order> today = orderMapper.selectCreatedSince(LocalDate.now().atStartOfDay());
        long filled = today.stream()
                .filter(order -> OrderStatus.FILLED.getCode().equals(order.getStatus()))
                .count();
        Map<String, Object> orders = new LinkedHashMap<>();
        orders.put("active", active);
        orders.put("filledToday", filled);
        return orders;
    }

    private String safeAggregateStatus() {
        try {
            return healthEndpoint.health().getStatus().getCode();
        } catch (RuntimeException ex) {
            return "UNKNOWN";
        }
    }

    private String healthStatus(String... path) {
        try {
            HealthComponent component = healthEndpoint.healthForPath(path);
            return component == null ? "UNKNOWN" : component.getStatus().getCode();
        } catch (RuntimeException ex) {
            return "UNKNOWN";
        }
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal ratio(BigDecimal part, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(BigDecimal.valueOf(100))
                .divide(total, 1, RoundingMode.HALF_UP);
    }
}
