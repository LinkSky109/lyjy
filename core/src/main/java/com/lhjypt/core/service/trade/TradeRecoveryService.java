package com.lhjypt.core.service.trade;

import com.lhjypt.core.config.DistributedLock;
import com.lhjypt.core.model.Order;
import com.lhjypt.core.model.trade.OrderReport;
import com.lhjypt.core.service.order.OrderManagementService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 从柜台查询活跃订单，补偿缺失回报。
 */
@Service
@RequiredArgsConstructor
public class TradeRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(TradeRecoveryService.class);

    private final OrderManagementService orderManagementService;
    private final TradeExecutionService tradeExecutionService;
    private final DistributedLock distributedLock;

    public int recoverActiveOrders() {
        if (!tradeExecutionService.isAuthenticated()) {
            throw new IllegalStateException("交易网关未登录，无法恢复活跃订单");
        }

        List<Order> activeOrders = orderManagementService.getActiveOrders();
        int recovered = 0;
        for (Order order : activeOrders) {
            String exchangeOrderId = order.getExchangeOrderId();
            if (exchangeOrderId == null || exchangeOrderId.isBlank()) {
                continue;
            }
            try {
                String gatewayId = order.getGatewayId() == null
                        || order.getGatewayId().isBlank()
                        ? tradeExecutionService.getDefaultGatewayId()
                        : order.getGatewayId();
                OrderReport report = tradeExecutionService.queryOrder(
                        gatewayId, exchangeOrderId);
                if (report != null) {
                    orderManagementService.applyOrderReport(report);
                    recovered++;
                }
            } catch (Exception error) {
                log.warn("订单恢复查询失败: orderId={} exchangeOrderId={} error={}",
                        order.getOrderId(), exchangeOrderId, error.getMessage());
            }
        }
        log.info("活跃订单恢复完成: total={} recovered={}", activeOrders.size(), recovered);
        return recovered;
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void scheduledRecovery() {
        distributedLock.execute("trade.recovery", () -> {
            try {
                recoverActiveOrders();
            } catch (Exception error) {
                log.debug("定时订单恢复跳过: {}", error.getMessage());
            }
        });
    }
}
