package com.lhjypt.core.service.trade;

import com.lhjypt.core.mapper.GatewayReportLogMapper;
import com.lhjypt.core.model.Order;
import com.lhjypt.core.model.trade.GatewayReportLog;
import com.lhjypt.core.model.trade.OrderReport;
import com.lhjypt.core.model.trade.TradeReport;
import com.lhjypt.core.service.order.OrderManagementService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 回报幂等落库与业务分发。
 */
@Service
@RequiredArgsConstructor
public class GatewayReportHandlingService {

    private static final Logger log = LoggerFactory.getLogger(GatewayReportHandlingService.class);
    private static final String DEFAULT_GATEWAY_ID = "ACTIVE";

    private final GatewayReportLogMapper reportLogMapper;
    private final OrderManagementService orderManagementService;
    private final TradeProcessingService tradeProcessingService;

    @Transactional
    public void handleOrderReport(OrderReport report) {
        String key = orderReportKey(report);
        GatewayReportLog logEntry = claim(
                gatewayId(report.getGatewayId()), sessionId(report.getGatewaySessionId()),
                "ORDER", key, report.toString());
        if (logEntry == null) {
            return;
        }
        orderManagementService.applyOrderReport(report);
        markProcessed(logEntry);
    }

    @Transactional
    public void handleTradeReport(TradeReport report) {
        GatewayReportLog logEntry = claim(
                gatewayId(report.getGatewayId()), sessionId(report.getGatewaySessionId()),
                "TRADE",
                report.getTradeId(), report.toString());
        if (logEntry == null) {
            return;
        }
        Order order = orderManagementService.findByClientOrderIdOrBrokerRef(
                report.getClientOrderId(), report.getOrderRef());
        if (order == null) {
            throw new IllegalStateException(
                    "无法匹配成交回报: " + report.getTradeId());
        }
        tradeProcessingService.process(order, report);
        markProcessed(logEntry);
    }

    private GatewayReportLog claim(String gatewayId, String sessionId, String type,
                                   String key, String payload) {
        String scopedKey = sessionId + "|" + key;
        GatewayReportLog existing = reportLogMapper.selectByReportKey(
                gatewayId, type, scopedKey);
        if (existing != null) {
            return null;
        }
        GatewayReportLog entry = new GatewayReportLog();
        entry.setGatewayId(gatewayId);
        entry.setReportType(type);
        entry.setReportKey(scopedKey);
        entry.setPayloadHash(Integer.toHexString(payload.hashCode()));
        entry.setProcessStatus("RECEIVED");
        entry.setRetryCount(0);
        entry.setCreateTime(LocalDateTime.now());
        entry.setUpdateTime(LocalDateTime.now());
        try {
            reportLogMapper.insert(entry);
            return entry;
        } catch (DuplicateKeyException duplicate) {
            log.debug("重复回报已忽略: type={} key={}", type, key);
            return null;
        }
    }

    private void markProcessed(GatewayReportLog entry) {
        entry.setProcessStatus("PROCESSED");
        entry.setUpdateTime(LocalDateTime.now());
        reportLogMapper.updateById(entry);
    }

    private static String orderReportKey(OrderReport report) {
        return String.join("|",
                safe(report.getOrderRef()),
                safe(report.getExchangeOrderId()),
                safe(report.getOrderStatus()),
                report.getVolumeTraded() == null ? "" : report.getVolumeTraded().toPlainString());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String gatewayId(String value) {
        return value == null || value.isBlank() ? DEFAULT_GATEWAY_ID : value;
    }

    private static String sessionId(String value) {
        return value == null || value.isBlank() ? "NO-SESSION" : value;
    }
}
