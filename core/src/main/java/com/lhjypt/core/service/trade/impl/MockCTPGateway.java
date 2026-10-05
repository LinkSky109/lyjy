package com.lhjypt.core.service.trade.impl;

import com.lhjypt.core.model.trade.*;
import com.lhjypt.core.service.trade.TradingGateway;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MockCTP交易网关
 * 模拟CTP柜台的完整行为，用于开发测试：
 * - 模拟登录认证流程
 * - 模拟报单、成交、撤单回报
 * - 模拟持仓、资金查询
 * - 模拟订单状态流转
 *
 * 真实CTP接入时替换为CTPGatewayAdapter（对接native库）
 */
@Service
public class MockCTPGateway implements TradingGateway {
    private static final Logger log = LoggerFactory.getLogger(MockCTPGateway.class);


    private volatile boolean connected = false;
    private volatile boolean authenticated = false;
    private CTPConfig config;

    private final AtomicInteger orderRefCounter = new AtomicInteger(0);
    private final AtomicInteger tradeIdCounter = new AtomicInteger(0);

    /** 订单回报存储 */
    private final Map<String, OrderReport> orders = new ConcurrentHashMap<>();
    /** 成交回报存储 */
    private final List<TradeReport> trades = new CopyOnWriteArrayList<>();
    /** 持仓存储 */
    private final Map<String, PositionDetail> positions = new ConcurrentHashMap<>();

    /** 账户信息 */
    private AccountInfo account;

    private final List<OrderReportListener> orderListeners = new CopyOnWriteArrayList<>();
    private final List<TradeReportListener> tradeListeners = new CopyOnWriteArrayList<>();

    @Override
    public void connect(CTPConfig config) {
        this.config = config;
        log.info("[MockCTP] 正在连接交易前置: {}", config.getTdFrontAddress());

        // 模拟网络连接延迟
        simulateDelay(200);

        connected = true;
        log.info("[MockCTP] 交易前置连接成功");

        // 自动登录认证
        authenticate(config);
    }

    private void authenticate(CTPConfig config) {
        log.info("[MockCTP] 正在认证登录: broker={} user={}",
                config.getBrokerId(), config.getUserId());
        simulateDelay(300);

        if (config.getUserId() == null || config.getUserId().isBlank()) {
            log.error("[MockCTP] 认证失败：用户ID为空");
            return;
        }

        authenticated = true;
        initAccount();
        log.info("[MockCTP] 认证登录成功，交易日: {}", getTradingDay());
    }

    private void initAccount() {
        account = new AccountInfo();
        account.setAccountId(config.getUserId());
        account.setBalance(new BigDecimal("1000000"));
        account.setAvailable(new BigDecimal("1000000"));
        account.setFrozen(BigDecimal.ZERO);
        account.setMargin(BigDecimal.ZERO);
        account.setPositionPnl(BigDecimal.ZERO);
        account.setClosePnl(BigDecimal.ZERO);
        account.setCommission(BigDecimal.ZERO);
        account.setRiskDegree(BigDecimal.ZERO);
    }

    @Override
    public void disconnect() {
        authenticated = false;
        connected = false;
        log.info("[MockCTP] 已断开连接");
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public String submitOrder(TradeRequest request) {
        if (!authenticated) {
            throw new IllegalStateException("未登录，无法报单");
        }

        String orderRef = String.format("%06d", orderRefCounter.incrementAndGet());
        String exchangeOrderId = "EX" + orderRef;

        log.info("[MockCTP] 收到报单: ref={} {} {} x{} @{}",
                orderRef, request.getSymbol(), request.getDirection(),
                request.getQuantity(), request.getPrice());

        // 市价单兜底：未指定价格时，平仓取对侧持仓成本价，开仓取持仓成本价或默认参考价
        if (request.getPrice() == null) {
            request.setPrice(resolveMarketPrice(request));
        }

        // 创建订单回报（未成交状态）
        OrderReport report = new OrderReport();
        report.setOrderRef(orderRef);
        report.setExchangeOrderId(exchangeOrderId);
        report.setSymbol(request.getSymbol());
        report.setDirection(request.getDirection());
        report.setOffsetFlag(request.getOffsetFlag());
        report.setLimitPrice(request.getPrice());
        report.setVolumeTotalOriginal(request.getQuantity());
        report.setVolumeTraded(BigDecimal.ZERO);
        report.setOrderStatus("NOT_TRADED");
        report.setInsertTime(LocalDateTime.now());
        report.setUpdateTime(LocalDateTime.now());
        orders.put(exchangeOrderId, report);

        // 通知订单监听器
        notifyOrderReport(report);

        // 模拟成交（限价单立即全部成交，模拟高流动性）
        simulateDelay(100);
        simulateFill(report, request);

        return orderRef;
    }

    /**
     * 模拟订单成交
     */
    private void simulateFill(OrderReport report, TradeRequest request) {
        // 更新订单状态为全部成交
        report.setOrderStatus("ALL_TRADED");
        report.setVolumeTraded(request.getQuantity());
        report.setUpdateTime(LocalDateTime.now());
        notifyOrderReport(report);

        // 生成成交回报
        String tradeId = String.format("T%06d", tradeIdCounter.incrementAndGet());
        TradeReport trade = new TradeReport();
        trade.setTradeId(tradeId);
        trade.setOrderRef(report.getOrderRef());
        trade.setExchangeOrderId(report.getExchangeOrderId());
        trade.setSymbol(request.getSymbol());
        trade.setExchange(request.getExchange());
        trade.setDirection(request.getDirection());
        trade.setOffsetFlag(request.getOffsetFlag());
        trade.setPrice(request.getPrice());
        trade.setVolume(request.getQuantity());
        trade.setTurnover(request.getPrice().multiply(request.getQuantity())
                .setScale(8, RoundingMode.HALF_UP));
        trade.setTradeTime(LocalDateTime.now());
        trade.setTradingDay(getTradingDay());
        trades.add(trade);

        // 更新持仓
        updatePosition(request);

        // 更新账户
        updateAccount(request, trade);

        // 通知成交监听器
        notifyTradeReport(trade);

        log.info("[MockCTP] 成交: trade={} {} x{} @{}",
                tradeId, request.getSymbol(), request.getQuantity(), request.getPrice());
    }

    /**
     * 市价单兜底成交价：
     * - 平仓：取被平方向的持仓成本价（SELL平多→LONG持仓；BUY平空→SHORT持仓）
     * - 开仓：已有同向持仓取其成本价，否则默认参考价
     */
    private BigDecimal resolveMarketPrice(TradeRequest request) {
        String holdingSide;
        if ("CLOSE".equals(request.getOffsetFlag())) {
            holdingSide = "SELL".equals(request.getDirection()) ? "LONG" : "SHORT";
        } else {
            holdingSide = "BUY".equals(request.getDirection()) ? "LONG" : "SHORT";
        }
        PositionDetail p = positions.get(request.getSymbol() + "-" + holdingSide);
        if (p != null && p.getAvgCost() != null && p.getAvgCost().compareTo(BigDecimal.ZERO) > 0) {
            return p.getAvgCost();
        }
        return new BigDecimal("3500");
    }

    private void updatePosition(TradeRequest request) {
        // 开仓：BUY→LONG / SELL→SHORT；平仓：SELL→平LONG / BUY→平SHORT
        boolean isClose = !"OPEN".equals(request.getOffsetFlag());
        String side;
        if (isClose) {
            side = "SELL".equals(request.getDirection()) ? "LONG" : "SHORT";
        } else {
            side = "BUY".equals(request.getDirection()) ? "LONG" : "SHORT";
        }
        String key = request.getSymbol() + "-" + side;

        PositionDetail position = positions.computeIfAbsent(key, k -> {
            PositionDetail p = new PositionDetail();
            p.setSymbol(request.getSymbol());
            p.setExchange(request.getExchange());
            p.setDirection(side);
            p.setTotalVolume(BigDecimal.ZERO);
            p.setTodayVolume(BigDecimal.ZERO);
            p.setYesterdayVolume(BigDecimal.ZERO);
            p.setAvailableVolume(BigDecimal.ZERO);
            p.setAvgCost(request.getPrice());
            return p;
        });

        BigDecimal newVolume;
        if (!isClose) {
            newVolume = position.getTotalVolume().add(request.getQuantity());
            position.setTotalVolume(newVolume);
            position.setTodayVolume(position.getTodayVolume().add(request.getQuantity()));
            position.setAvailableVolume(newVolume);
        } else {
            newVolume = position.getTotalVolume().subtract(request.getQuantity());
            position.setTotalVolume(newVolume);
            position.setAvailableVolume(newVolume);
        }
    }

    private void updateAccount(TradeRequest request, TradeReport trade) {
        BigDecimal turnover = trade.getTurnover();
        BigDecimal commission = turnover.multiply(new BigDecimal("0.0003"))
                .setScale(2, RoundingMode.HALF_UP);

        if ("OPEN".equals(request.getOffsetFlag())) {
            // 开仓：冻结/扣减资金，增加保证金
            BigDecimal margin = turnover.multiply(new BigDecimal("0.1"))
                    .setScale(2, RoundingMode.HALF_UP);
            account.setAvailable(account.getAvailable().subtract(margin).subtract(commission));
            account.setMargin(account.getMargin().add(margin));
        } else {
            // 平仓：释放保证金，计算盈亏
            BigDecimal margin = turnover.multiply(new BigDecimal("0.1"))
                    .setScale(2, RoundingMode.HALF_UP);
            account.setAvailable(account.getAvailable().add(margin).subtract(commission));
            account.setMargin(account.getMargin().subtract(margin));
        }

        account.setCommission(account.getCommission().add(commission));
        account.setRiskDegree(account.getMargin()
                .divide(account.getBalance(), 4, RoundingMode.HALF_UP));
    }

    @Override
    public boolean cancelOrder(String exchangeOrderId, String symbol) {
        OrderReport report = orders.get(exchangeOrderId);
        if (report == null) return false;

        if ("ALL_TRADED".equals(report.getOrderStatus())) {
            log.warn("[MockCTP] 撤单失败，订单已全部成交: {}", exchangeOrderId);
            return false;
        }

        report.setOrderStatus("CANCELED");
        report.setUpdateTime(LocalDateTime.now());
        notifyOrderReport(report);
        log.info("[MockCTP] 撤单成功: {}", exchangeOrderId);
        return true;
    }

    @Override
    public OrderReport queryOrder(String exchangeOrderId) {
        return orders.get(exchangeOrderId);
    }

    @Override
    public List<TradeReport> queryTrades() {
        return Collections.unmodifiableList(trades);
    }

    @Override
    public List<PositionDetail> queryPositions() {
        return new ArrayList<>(positions.values());
    }

    @Override
    public AccountInfo queryAccount() {
        return account;
    }

    @Override
    public void addOrderReportListener(OrderReportListener listener) {
        orderListeners.add(listener);
    }

    @Override
    public void addTradeReportListener(TradeReportListener listener) {
        tradeListeners.add(listener);
    }

    @Override
    public String getName() {
        return "MOCK_CTP";
    }

    private void notifyOrderReport(OrderReport report) {
        for (OrderReportListener l : orderListeners) {
            l.onOrderReport(report);
        }
    }

    private void notifyTradeReport(TradeReport report) {
        for (TradeReportListener l : tradeListeners) {
            l.onTradeReport(report);
        }
    }

    private String getTradingDay() {
        return LocalDateTime.now().toLocalDate().toString().replace("-", "");
    }

    private void simulateDelay(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
