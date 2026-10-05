package com.lhjypt.core.service.trade;

import com.lhjypt.core.model.gateway.GatewayInfo;
import com.lhjypt.core.model.gateway.GatewayStatus;
import com.lhjypt.core.model.gateway.GatewaySubmission;
import com.lhjypt.core.model.trade.AccountInfo;
import com.lhjypt.core.model.trade.CTPConfig;
import com.lhjypt.core.model.trade.GatewayMode;
import com.lhjypt.core.model.trade.OrderReport;
import com.lhjypt.core.model.trade.PositionDetail;
import com.lhjypt.core.model.trade.TradeReport;
import com.lhjypt.core.model.trade.TradeRequest;
import com.lhjypt.core.service.trade.gateway.GatewayRegistry;
import com.lhjypt.core.service.trade.gateway.GatewayRouter;
import com.lhjypt.core.service.trade.gateway.ManagedGateway;
import com.lhjypt.core.service.trade.impl.CTPGatewayAdapter;
import com.lhjypt.core.service.trade.impl.MockCTPGateway;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 交易执行服务（EMS）。
 *
 * 业务层统一通过 GatewayRouter 访问一个或多个交易网关。
 */
@Service
@RequiredArgsConstructor
public class TradeExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TradeExecutionService.class);
    public static final String DEFAULT_MOCK_GATEWAY = "mock-default";
    public static final String LIVE_CTP_GATEWAY = "ctp-live";

    private final MockCTPGateway mockGateway;
    private final CTPGatewayAdapter ctpGateway;
    private final GatewayRouter gatewayRouter;

    private TradingGateway currentGateway;
    private GatewayMode currentMode;
    private String defaultGatewayId;

    @Value("${lhjypt.trade.mode:MOCK}")
    private String configuredMode;

    @PostConstruct
    public void initializeDefaultGateway() {
        GatewayInfo mockInfo = gatewayInfo(
                DEFAULT_MOCK_GATEWAY, "MOCK", "默认 Mock CTP", 100);
        gatewayRouter.register(mockInfo, mockGateway);
        defaultGatewayId = DEFAULT_MOCK_GATEWAY;
    }

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
            gatewayRouter.register(gatewayInfo(
                    LIVE_CTP_GATEWAY, "CTP", "真实 CTP 网关", 10), ctpGateway);
            defaultGatewayId = LIVE_CTP_GATEWAY;
            currentGateway = ctpGateway;
        } else {
            defaultGatewayId = DEFAULT_MOCK_GATEWAY;
            currentGateway = mockGateway;
        }

        gatewayRouter.registry().connect(defaultGatewayId, config);
        currentMode = requestedMode;
        log.info("交易网关已连接: mode={} gateway={}",
                requestedMode, gatewayRouter.registry().get(defaultGatewayId).info().getGatewayId());
    }

    public void connectGateway(String gatewayId, CTPConfig config) {
        gatewayRouter.registry().connect(gatewayId, config);
        defaultGatewayId = gatewayId;
    }

    public void disconnect() {
        if (defaultGatewayId != null) {
            gatewayRouter.registry().disconnect(defaultGatewayId);
        }
        currentGateway = null;
        currentMode = null;
    }

    /** 兼容旧入口，返回柜台订单引用。 */
    public String placeOrder(TradeRequest request) {
        return placeOrderRouted(request).orderRef();
    }

    public GatewaySubmission placeOrderRouted(TradeRequest request) {
        return gatewayRouter.submitOrder(request);
    }

    public boolean cancelOrder(String exchangeOrderId, String symbol) {
        checkReady();
        return gatewayRouter.cancelOrder(defaultGatewayId, exchangeOrderId, symbol);
    }

    public boolean cancelOrder(String gatewayId, String exchangeOrderId, String symbol) {
        return gatewayRouter.cancelOrder(gatewayId, exchangeOrderId, symbol);
    }

    public AccountInfo queryAccount() {
        checkReady();
        return gatewayRouter.queryAccount(defaultGatewayId);
    }

    public AccountInfo queryAccount(String gatewayId) {
        return gatewayRouter.queryAccount(gatewayId);
    }

    public List<PositionDetail> queryPositions() {
        return gatewayRouter.queryPositionsAll();
    }

    public List<TradeReport> queryTrades() {
        checkReady();
        return gatewayRouter.queryTrades(defaultGatewayId);
    }

    public OrderReport queryOrder(String exchangeOrderId) {
        return queryOrder(defaultGatewayId, exchangeOrderId);
    }

    public OrderReport queryOrder(String gatewayId, String exchangeOrderId) {
        return gatewayRouter.queryOrder(gatewayId, exchangeOrderId);
    }

    public boolean isConnected() {
        return gatewayRouter.isAnyConnected();
    }

    public boolean isAuthenticated() {
        return gatewayRouter.isAnyAuthenticated();
    }

    public String getMode() {
        if (currentMode != null) {
            return currentMode.name();
        }
        return GatewayMode.from(configuredMode).name();
    }

    public String getDefaultGatewayId() {
        return defaultGatewayId;
    }

    public void registerReportListeners(TradingGateway.OrderReportListener orderListener,
                                        TradingGateway.TradeReportListener tradeListener) {
        gatewayRouter.registerReportListeners(orderListener, tradeListener);
    }

    private void checkReady() {
        if (currentGateway == null || !currentGateway.isAuthenticated()) {
            throw new IllegalStateException("交易网关未连接或未登录，请先连接柜台");
        }
    }

    private static GatewayInfo gatewayInfo(String id, String type,
                                           String name, int priority) {
        GatewayInfo info = new GatewayInfo();
        info.setGatewayId(id);
        info.setGatewayType(type);
        info.setName(name);
        info.setStatus(GatewayStatus.DOWN.name());
        info.setParamConfig("{}");
        info.setPriority(priority);
        return info;
    }
}
