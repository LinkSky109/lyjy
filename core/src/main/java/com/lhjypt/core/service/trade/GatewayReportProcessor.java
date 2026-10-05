package com.lhjypt.core.service.trade;

import com.lhjypt.core.model.trade.OrderReport;
import com.lhjypt.core.model.trade.TradeReport;
import com.lhjypt.core.service.trade.TradingGateway.OrderReportListener;
import com.lhjypt.core.service.trade.TradingGateway.TradeReportListener;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 将网关回报接入业务处理管线。
 */
@Service
@RequiredArgsConstructor
public class GatewayReportProcessor implements OrderReportListener, TradeReportListener {

    private static final Logger log = LoggerFactory.getLogger(GatewayReportProcessor.class);

    private final TradeExecutionService executionService;
    private final GatewayReportHandlingService handlingService;

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        executionService.registerReportListeners(this, this);
        log.info("网关回报监听器已注册");
    }

    @Override
    public void onOrderReport(OrderReport report) {
        try {
            handlingService.handleOrderReport(report);
        } catch (Exception error) {
            log.error("处理订单回报失败: ref={} status={}",
                    report.getOrderRef(), report.getOrderStatus(), error);
        }
    }

    @Override
    public void onTradeReport(TradeReport report) {
        try {
            handlingService.handleTradeReport(report);
        } catch (Exception error) {
            log.error("处理成交回报失败: tradeId={}", report.getTradeId(), error);
        }
    }
}
