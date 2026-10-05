package com.lhjypt.core.service.trade;

import com.lhjypt.core.model.trade.*;
import com.lhjypt.core.service.trade.impl.CTPGatewayAdapter;
import com.lhjypt.core.service.trade.impl.MockCTPGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 交易执行服务（EMS）
 * 统一管理交易网关，提供报单、撤单、查询的业务入口
 * 协调风控校验与订单执行
 */
@Service
@RequiredArgsConstructor
public class TradeExecutionService {
    private static final Logger log = LoggerFactory.getLogger(TradeExecutionService.class);


    private final MockCTPGateway mockGateway;
    private final CTPGatewayAdapter ctpGateway;

    /** 当前使用的网关 */
    private TradingGateway currentGateway;
    private GatewayMode currentMode;

    @Value("${lhjypt.trade.mode:MOCK}")
    private String configuredMode;

    /**
     * 连接柜台
     */
    public void connect(CTPConfig config) {
        GatewayMode requestedMode = GatewayMode.from(
                config.getMode() == null || config.getMode().isBlank()
                        ? configuredMode
                        : config.getMode());

        if (requestedMode == GatewayMode.LIVE) {
            if (!ctpGateway.isAvailable()) {
                throw new IllegalStateException(
                        "LIVE 模式不可用：真实 CTP 适配器尚未实现，禁止回退到 Mock 网关");
            }
            currentGateway = ctpGateway;
        } else {
            currentGateway = mockGateway;
        }

        currentGateway.connect(config);
        currentMode = requestedMode;
        log.info("交易网关已连接: mode={} gateway={}",
                requestedMode, currentGateway.getName());
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        if (currentGateway != null) {
            currentGateway.disconnect();
        }
        currentMode = null;
    }

    /**
     * 下单
     */
    public String placeOrder(TradeRequest request) {
        checkReady();
        return currentGateway.submitOrder(request);
    }

    /**
     * 撤单
     */
    public boolean cancelOrder(String exchangeOrderId, String symbol) {
        checkReady();
        return currentGateway.cancelOrder(exchangeOrderId, symbol);
    }

    /**
     * 查询账户
     */
    public AccountInfo queryAccount() {
        checkReady();
        return currentGateway.queryAccount();
    }

    /**
     * 查询持仓
     */
    public List<PositionDetail> queryPositions() {
        checkReady();
        return currentGateway.queryPositions();
    }

    /**
     * 查询成交
     */
    public List<TradeReport> queryTrades() {
        checkReady();
        return currentGateway.queryTrades();
    }

    /**
     * 查询订单
     */
    public OrderReport queryOrder(String exchangeOrderId) {
        checkReady();
        return currentGateway.queryOrder(exchangeOrderId);
    }

    /**
     * 连接状态
     */
    public boolean isConnected() {
        return currentGateway != null && currentGateway.isConnected();
    }

    /**
     * 登录状态
     */
    public boolean isAuthenticated() {
        return currentGateway != null && currentGateway.isAuthenticated();
    }

    public String getMode() {
        if (currentMode != null) {
            return currentMode.name();
        }
        return GatewayMode.from(configuredMode).name();
    }

    /**
     * 向当前可用的网关实现注册回报监听器。
     */
    public void registerReportListeners(TradingGateway.OrderReportListener orderListener,
                                        TradingGateway.TradeReportListener tradeListener) {
        mockGateway.addOrderReportListener(orderListener);
        mockGateway.addTradeReportListener(tradeListener);
        ctpGateway.addOrderReportListener(orderListener);
        ctpGateway.addTradeReportListener(tradeListener);
    }

    private void checkReady() {
        if (currentGateway == null || !currentGateway.isAuthenticated()) {
            throw new IllegalStateException("交易网关未连接或未登录，请先连接柜台");
        }
    }
}
