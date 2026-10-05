package com.lhjypt.core.service.backtest.impl;

import com.lhjypt.core.model.backtest.BacktestConfig;
import com.lhjypt.core.model.backtest.TradeRecord;
import com.lhjypt.core.model.market.TickData;
import com.lhjypt.core.service.backtest.MatchingEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tick级撮合引擎实现
 * 撮合规则：
 * - 限价买单：当 tick卖一价 <= 委托价 时成交，成交价为 max(委托价, 卖一价)
 * - 限价卖单：当 tick买一价 >= 委托价 时成交，成交价为 min(委托价, 买一价)
 * - 市价买单：以卖一价立即成交
 * - 市价卖单：以买一价立即成交
 * - 涨跌停限制：涨停不能买入，跌停不能卖出
 */
@Service
@RequiredArgsConstructor
public class TickMatchingEngine implements MatchingEngine {
    private static final Logger log = LoggerFactory.getLogger(TickMatchingEngine.class);


    private final TransactionCostCalculator costCalculator;

    private BacktestConfig config;
    private final Map<String, Order> pendingOrders = new ConcurrentHashMap<>();
    private final AtomicLong orderIdGenerator = new AtomicLong(0);
    private final AtomicLong tradeIdGenerator = new AtomicLong(0);

    /** 当前最新tick（用于市价单成交） */
    private volatile TickData currentTick;

    @Override
    public void init(BacktestConfig config) {
        this.config = config;
        this.pendingOrders.clear();
        this.orderIdGenerator.set(0);
        this.tradeIdGenerator.set(0);
        this.currentTick = null;
    }

    @Override
    public String submitBuyLimit(String symbol, BigDecimal price, BigDecimal quantity) {
        String orderId = generateOrderId();
        Order order = new Order(orderId, symbol, "BUY", "LIMIT", price, quantity, LocalDateTime.now());
        pendingOrders.put(orderId, order);
        return orderId;
    }

    @Override
    public String submitSellLimit(String symbol, BigDecimal price, BigDecimal quantity) {
        String orderId = generateOrderId();
        Order order = new Order(orderId, symbol, "SELL", "LIMIT", price, quantity, LocalDateTime.now());
        pendingOrders.put(orderId, order);
        return orderId;
    }

    @Override
    public String submitBuyMarket(String symbol, BigDecimal quantity) {
        // 市价单入队，在紧接着的 processTick 中以当前卖一价成交
        String orderId = generateOrderId();
        Order order = new Order(orderId, symbol, "BUY", "MARKET", null, quantity, LocalDateTime.now());
        pendingOrders.put(orderId, order);
        return orderId;
    }

    @Override
    public String submitSellMarket(String symbol, BigDecimal quantity) {
        // 市价单入队，在紧接着的 processTick 中以当前买一价成交
        String orderId = generateOrderId();
        Order order = new Order(orderId, symbol, "SELL", "MARKET", null, quantity, LocalDateTime.now());
        pendingOrders.put(orderId, order);
        return orderId;
    }

    @Override
    public boolean cancelOrder(String orderId) {
        return pendingOrders.remove(orderId) != null;
    }

    @Override
    public List<TradeRecord> processTick(TickData tick) {
        this.currentTick = tick;
        List<TradeRecord> trades = new ArrayList<>();

        if (tick == null) return trades;

        Iterator<Map.Entry<String, Order>> iterator = pendingOrders.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Order> entry = iterator.next();
            Order order = entry.getValue();

            // 只撮合对应标的的订单
            if (!order.symbol.equals(tick.getSymbol())) continue;

            boolean matched = false;
            BigDecimal matchPrice = null;
            String matchReason = "LIMIT_PRICE";

            if ("BUY".equals(order.direction)) {
                if ("MARKET".equals(order.type)) {
                    // 市价买单：以卖一价成交，无卖一价时用最新价
                    BigDecimal px = tick.getAskPrice1() != null ? tick.getAskPrice1() : tick.getLastPrice();
                    if (px != null) {
                        matchPrice = px;
                        matched = true;
                        matchReason = "MARKET";
                    }
                } else if (tick.getAskPrice1() != null &&
                        tick.getAskPrice1().compareTo(order.price) <= 0) {
                    // 限价买单：卖一价 <= 委托价时成交
                    matchPrice = order.price.max(tick.getAskPrice1());
                    matched = true;
                }
            } else if ("SELL".equals(order.direction)) {
                if ("MARKET".equals(order.type)) {
                    // 市价卖单：以买一价成交，无买一价时用最新价
                    BigDecimal px = tick.getBidPrice1() != null ? tick.getBidPrice1() : tick.getLastPrice();
                    if (px != null) {
                        matchPrice = px;
                        matched = true;
                        matchReason = "MARKET";
                    }
                } else if (tick.getBidPrice1() != null &&
                        tick.getBidPrice1().compareTo(order.price) >= 0) {
                    // 限价卖单：买一价 >= 委托价时成交
                    matchPrice = order.price.min(tick.getBidPrice1());
                    matched = true;
                }
            }

            if (matched) {
                // 涨跌停检查
                if (isLimitUp(tick) && "BUY".equals(order.direction)) continue;
                if (isLimitDown(tick) && "SELL".equals(order.direction)) continue;

                TradeRecord trade = createTrade(order.orderId, order.symbol, order.direction,
                        matchReason, matchPrice, order.quantity, tick.getTickTime());
                trades.add(trade);
                iterator.remove();
            }
        }

        return trades;
    }

    @Override
    public int getPendingOrderCount() {
        return pendingOrders.size();
    }

    @Override
    public void reset() {
        pendingOrders.clear();
        currentTick = null;
    }

    private TradeRecord createTrade(String orderId, String symbol, String direction,
                                    String matchReason, BigDecimal price,
                                    BigDecimal quantity, LocalDateTime time) {
        TradeRecord trade = new TradeRecord();
        trade.setTradeId(generateTradeId());
        trade.setBacktestId(config != null ? config.getBacktestId() : "");
        trade.setSymbol(symbol);
        trade.setDirection(direction);
        trade.setPrice(price);
        trade.setQuantity(quantity);
        trade.setTurnover(price.multiply(quantity).setScale(8, RoundingMode.HALF_UP));
        trade.setMatchReason(matchReason);
        trade.setTradeTime(time);
        trade.setOrderId(orderId);

        // 计算交易成本
        if (costCalculator != null && config != null) {
            costCalculator.calculateCost(trade, config);
        }

        return trade;
    }

    private boolean isLimitUp(TickData tick) {
        if (tick.getPreClose() == null || tick.getLastPrice() == null) return false;
        BigDecimal limitUp = tick.getPreClose().multiply(new BigDecimal("1.10"))
                .setScale(2, RoundingMode.HALF_UP);
        return tick.getLastPrice().compareTo(limitUp) >= 0;
    }

    private boolean isLimitDown(TickData tick) {
        if (tick.getPreClose() == null || tick.getLastPrice() == null) return false;
        BigDecimal limitDown = tick.getPreClose().multiply(new BigDecimal("0.90"))
                .setScale(2, RoundingMode.HALF_UP);
        return tick.getLastPrice().compareTo(limitDown) <= 0;
    }

    private String generateOrderId() {
        return "ORD-" + System.currentTimeMillis() + "-" + orderIdGenerator.incrementAndGet();
    }

    private String generateTradeId() {
        return "TRD-" + System.currentTimeMillis() + "-" + tradeIdGenerator.incrementAndGet();
    }

    /**
     * 内部订单类
     */
    private static class Order {
        String orderId;
        String symbol;
        String direction;
        String type;
        BigDecimal price;
        BigDecimal quantity;
        LocalDateTime submitTime;

        Order(String orderId, String symbol, String direction, String type,
              BigDecimal price, BigDecimal quantity, LocalDateTime submitTime) {
            this.orderId = orderId;
            this.symbol = symbol;
            this.direction = direction;
            this.type = type;
            this.price = price;
            this.quantity = quantity;
            this.submitTime = submitTime;
        }
    }
}
