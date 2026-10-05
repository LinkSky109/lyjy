package com.lhjypt.core.service.trade.impl;

import com.lhjypt.core.model.trade.*;
import com.lhjypt.core.service.trade.TradingGateway;
import org.springframework.stereotype.Service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 真实CTP柜台适配层（预留）
 *
 * 对接CTP原生SDK（C++ API的Java/JNI封装，如 ctp-api / vnpy-java）
 * 实现方式：
 * 1. 引入CTP Java封装库（JNI调用C++ ThostTraderApi）
 * 2. 实现Spi回调（OnRtnOrder/OnRtnTrade/OnRspQryInvestorPosition等）
 * 3. 处理连接、认证、报单、查询的异步流程
 *
 * CTP API核心类：
 * - ThostTraderApi: 交易API（CreateFtdcTraderApi）
 * - ThostTraderSpi: 交易回调（OnFrontConnected/OnRspUserLogin/OnRtnOrder/OnRtnTrade）
 * - ThostMdApi: 行情API
 *
 * 部署要求：
 * - 需安装CTP SDK动态库（thosttraderapi_se.so/dll）
 * - 需配置java.library.path
 * - 需有效的CTP账户和前置地址
 */
@Service
public class CTPGatewayAdapter implements TradingGateway {
    private static final Logger log = LoggerFactory.getLogger(CTPGatewayAdapter.class);


    private volatile boolean connected = false;
    private volatile boolean authenticated = false;

    /** 原生交易API句柄（JNI） */
    private Object nativeTraderApi;

    @Override
    public void connect(CTPConfig config) {
        // TODO: P2阶段实现真实CTP对接
        // 1. CreateFtdcTraderApi 创建实例
        // 2. RegisterSpi 注册回调
        // 3. RegisterFront 注册前置地址
        // 4. SubscribePublicTopic/SubscribePrivateTopic
        // 5. Init 初始化
        // 6. OnFrontConnected回调后 ReqUserLogin
        // 7. ReqAuthenticate 认证
        // 8. ReqSettlementInfoConfirm 结算单确认
        log.warn("真实CTP适配层尚未实现，当前使用MockCTPGateway。请在P2阶段对接CTP原生SDK。");
        throw new UnsupportedOperationException("真实CTP接入待实现，请使用MockCTPGateway");
    }

    /**
     * 当前没有真实 CTP 原生 SDK，LIVE 模式必须由上层快速失败。
     */
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void disconnect() {
        // Release() 释放API
        connected = false;
        authenticated = false;
    }

    @Override
    public boolean isConnected() { return connected; }

    @Override
    public boolean isAuthenticated() { return authenticated; }

    @Override
    public String submitOrder(TradeRequest request) {
        // ReqOrderInsert，构造InputOrderField
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean cancelOrder(String exchangeOrderId, String symbol) {
        // ReqOrderAction
        throw new UnsupportedOperationException();
    }

    @Override
    public OrderReport queryOrder(String exchangeOrderId) {
        // ReqQryOrder
        throw new UnsupportedOperationException();
    }

    @Override
    public List<TradeReport> queryTrades() {
        // ReqQryTrade
        throw new UnsupportedOperationException();
    }

    @Override
    public List<PositionDetail> queryPositions() {
        // ReqQryInvestorPosition
        throw new UnsupportedOperationException();
    }

    @Override
    public AccountInfo queryAccount() {
        // ReqQryTradingAccount
        throw new UnsupportedOperationException();
    }

    @Override
    public void addOrderReportListener(OrderReportListener listener) {
        // 转发Spi OnRtnOrder回调
    }

    @Override
    public void addTradeReportListener(TradeReportListener listener) {
        // 转发Spi OnRtnTrade回调
    }

    @Override
    public String getName() {
        return "LIVE_CTP";
    }
}
