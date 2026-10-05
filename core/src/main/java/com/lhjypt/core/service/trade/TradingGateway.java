package com.lhjypt.core.service.trade;

import com.lhjypt.core.model.trade.*;

import java.util.List;

/**
 * 交易网关接口
 * 抽象不同柜台（CTP/股票柜台/模拟柜台）的统一交易能力
 */
public interface TradingGateway {

    /**
     * 连接柜台
     */
    void connect(CTPConfig config);

    /**
     * 断开连接
     */
    void disconnect();

    /**
     * 是否已连接
     */
    boolean isConnected();

    /**
     * 是否已认证登录
     */
    boolean isAuthenticated();

    /**
     * 提交订单
     * @return 订单引用号
     */
    String submitOrder(TradeRequest request);

    /**
     * 撤销订单
     */
    boolean cancelOrder(String exchangeOrderId, String symbol);

    /**
     * 查询订单
     */
    OrderReport queryOrder(String exchangeOrderId);

    /**
     * 查询当日成交
     */
    List<TradeReport> queryTrades();

    /**
     * 查询持仓
     */
    List<PositionDetail> queryPositions();

    /**
     * 查询账户资金
     */
    AccountInfo queryAccount();

    /**
     * 注册订单回报监听器
     */
    void addOrderReportListener(OrderReportListener listener);

    /**
     * 注册成交回报监听器
     */
    void addTradeReportListener(TradeReportListener listener);

    /**
     * 网关名称
     */
    String getName();

    /** 订单回报监听器 */
    interface OrderReportListener {
        void onOrderReport(OrderReport report);
    }

    /** 成交回报监听器 */
    interface TradeReportListener {
        void onTradeReport(TradeReport report);
    }
}
