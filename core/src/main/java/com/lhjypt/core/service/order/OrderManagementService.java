package com.lhjypt.core.service.order;

import com.alibaba.fastjson2.JSON;
import com.lhjypt.core.mapper.OrderMapper;
import com.lhjypt.core.model.Order;
import com.lhjypt.core.model.order.CreateOrderCommand;
import com.lhjypt.core.model.trade.OrderReport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OMS订单管理服务
 *
 * 订单全生命周期管理：
 * 1. 创建订单（幂等校验 → 生成订单号 → 持久化）
 * 2. 提交柜台
 * 3. 接收柜台回报，更新订单状态
 * 4. 处理成交，更新成交数量
 * 5. 订单查询
 *
 * 所有状态流转严格通过OrderStateMachine校验
 */
@Service
@RequiredArgsConstructor
public class OrderManagementService {
    private static final Logger log = LoggerFactory.getLogger(OrderManagementService.class);


    private final OrderMapper orderMapper;
    private final OrderStateMachine stateMachine;
    private final OrderNumberGenerator numberGenerator;
    private final OrderIdempotentService idempotentService;

    /**
     * 创建订单（本地，未提交柜台）
     */
    @Transactional
    public Order createOrder(CreateOrderCommand command) {
        // 0. 未显式提供幂等键时，基于请求内容自动生成（保证幂等且避免空键报错）
        if (command.getIdempotentKey() == null || command.getIdempotentKey().isBlank()) {
            command.setIdempotentKey("AUTO-" + sha256(JSON.toJSONString(command)));
        }
        // 1. 幂等校验
        String requestContent = JSON.toJSONString(command);
        var idempotentCheck = idempotentService.check(
                command.getIdempotentKey(), requestContent);

        // 重复请求直接返回原订单
        if (idempotentCheck.isDuplicate()) {
            return orderMapper.selectByOrderId(idempotentCheck.existingOrderId());
        }

        // 2. 创建订单实体
        Order order = new Order();
        order.setOrderId(numberGenerator.generate());
        order.setStrategyId(command.getStrategyId());
        order.setAccountId(command.getAccountId());
        order.setSymbol(command.getSymbol());
        order.setDirection(command.getDirection());
        order.setOffsetFlag(command.getOffsetFlag());
        order.setOrderType(command.getOrderType());
        order.setPrice(command.getPrice());
        order.setQuantity(command.getQuantity());
        order.setFilledQuantity(BigDecimal.ZERO);
        order.setAvgFillPrice(BigDecimal.ZERO);
        order.setStatus(OrderStatus.PENDING.getCode());
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        // 3. 持久化
        orderMapper.insert(order);

        // 4. 关联幂等记录
        idempotentService.complete(command.getIdempotentKey(), order.getOrderId());

        log.info("订单创建: orderId={} {} {} x{}",
                order.getOrderId(), order.getSymbol(),
                order.getDirection(), order.getQuantity());

        return order;
    }

    /**
     * 标记订单已提交柜台。
     *
     * 回报可能早于 submitOrder 返回，因此这里必须对已进入后续状态的订单保持幂等。
     */
    @Transactional
    public Order markSubmitted(String orderId, String brokerOrderRef) {
        Order order = getOrderByOrderId(orderId);
        OrderStatus current = OrderStatus.fromCode(order.getStatus());

        if (current == OrderStatus.PENDING) {
            stateMachine.validateTransit(current, OrderStatus.SUBMITTED);
            order.setStatus(OrderStatus.SUBMITTED.getCode());
        }

        if (brokerOrderRef != null && !brokerOrderRef.isBlank()) {
            order.setBrokerOrderRef(brokerOrderRef);
        }
        if (order.getSubmittedAt() == null) {
            order.setSubmittedAt(LocalDateTime.now());
        }
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
        return order;
    }

    /**
     * 标记订单被拒绝。
     */
    @Transactional
    public Order reject(String orderId, String errorCode, String errorMessage) {
        Order order = getOrderByOrderId(orderId);
        OrderStatus current = OrderStatus.fromCode(order.getStatus());
        if (current.isTerminal()) {
            return order;
        }
        stateMachine.validateTransit(current, OrderStatus.REJECTED);
        order.setStatus(OrderStatus.REJECTED.getCode());
        order.setErrorCode(errorCode);
        order.setErrorMessage(errorMessage);
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
        return order;
    }

    /**
     * 应用柜台订单回报。
     */
    @Transactional
    public Order applyOrderReport(OrderReport report) {
        Order order = findReportOrder(report.getClientOrderId(), report.getOrderRef());
        if (order == null) {
            throw new IllegalStateException("无法匹配柜台订单回报: "
                    + report.getOrderRef() + "/" + report.getClientOrderId());
        }

        if (report.getExchangeOrderId() != null) {
            order.setExchangeOrderId(report.getExchangeOrderId());
        }
        if (report.getOrderRef() != null) {
            order.setBrokerOrderRef(report.getOrderRef());
        }
        applyGatewayStatus(order, report.getOrderStatus(), report.getStatusMsg());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
        return order;
    }

    /**
     * 应用成交回报，累计成交数量和均价。
     */
    @Transactional
    public Order applyFill(String orderId, BigDecimal fillQuantity, BigDecimal fillPrice) {
        if (fillQuantity == null || fillQuantity.compareTo(BigDecimal.ZERO) <= 0
                || fillPrice == null || fillPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("成交数量和价格必须大于0");
        }

        Order order = getOrderByOrderId(orderId);
        BigDecimal previousFilled = order.getFilledQuantity() == null
                ? BigDecimal.ZERO : order.getFilledQuantity();
        BigDecimal previousAvg = order.getAvgFillPrice() == null
                ? BigDecimal.ZERO : order.getAvgFillPrice();

        if (previousFilled.compareTo(order.getQuantity()) >= 0) {
            return order;
        }

        BigDecimal newFilled = previousFilled.add(fillQuantity).min(order.getQuantity());
        BigDecimal appliedQuantity = newFilled.subtract(previousFilled);
        BigDecimal previousAmount = previousFilled.multiply(previousAvg);
        BigDecimal fillAmount = appliedQuantity.multiply(fillPrice);
        BigDecimal newAvg = previousAmount.add(fillAmount)
                .divide(newFilled, 8, RoundingMode.HALF_UP);

        order.setFilledQuantity(newFilled);
        order.setAvgFillPrice(newAvg);

        OrderStatus target = newFilled.compareTo(order.getQuantity()) >= 0
                ? OrderStatus.FILLED
                : OrderStatus.PARTIAL_FILLED;
        ensureStatus(order, target);
        if (target == OrderStatus.FILLED && order.getFilledAt() == null) {
            order.setFilledAt(LocalDateTime.now());
        }
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
        return order;
    }

    public Order findByClientOrderIdOrBrokerRef(String clientOrderId, String brokerOrderRef) {
        return findReportOrder(clientOrderId, brokerOrderRef);
    }

    private Order findReportOrder(String clientOrderId, String brokerOrderRef) {
        if (clientOrderId != null && !clientOrderId.isBlank()) {
            Order order = orderMapper.selectByOrderId(clientOrderId);
            if (order != null) {
                return order;
            }
        }
        if (brokerOrderRef != null && !brokerOrderRef.isBlank()) {
            return orderMapper.selectByBrokerOrderRef(brokerOrderRef);
        }
        return null;
    }

    private void applyGatewayStatus(Order order, String gatewayStatus, String statusMessage) {
        if (gatewayStatus == null) {
            return;
        }
        switch (gatewayStatus) {
            case "NOT_TRADED" -> ensureStatus(order, OrderStatus.SUBMITTED);
            case "PART_TRADED" -> ensureStatus(order, OrderStatus.PARTIAL_FILLED);
            case "ALL_TRADED" -> {
                ensureStatus(order, OrderStatus.FILLED);
                order.setFilledAt(LocalDateTime.now());
            }
            case "CANCELED" -> {
                ensureStatus(order, OrderStatus.CANCELED);
                order.setCanceledAt(LocalDateTime.now());
            }
            case "REJECTED" -> {
                ensureStatus(order, OrderStatus.REJECTED);
                order.setErrorCode("GATEWAY_REJECTED");
                order.setErrorMessage(statusMessage);
            }
            default -> log.warn("未知柜台订单状态: {}", gatewayStatus);
        }
    }

    private void ensureStatus(Order order, OrderStatus target) {
        OrderStatus current = OrderStatus.fromCode(order.getStatus());
        if (current == target) {
            return;
        }
        if (target == OrderStatus.PARTIAL_FILLED || target == OrderStatus.FILLED) {
            if (current == OrderStatus.PENDING) {
                current = move(order, current, OrderStatus.SUBMITTED);
            }
            if (current == OrderStatus.SUBMITTED) {
                current = move(order, current, OrderStatus.ACCEPTED);
            }
            if (current == OrderStatus.ACCEPTED && target == OrderStatus.PARTIAL_FILLED) {
                move(order, current, OrderStatus.PARTIAL_FILLED);
                return;
            }
        }
        move(order, current, target);
    }

    private OrderStatus move(Order order, OrderStatus from, OrderStatus to) {
        if (from == to) {
            return to;
        }
        stateMachine.validateTransit(from, to);
        order.setStatus(to.getCode());
        return to;
    }

    /**
     * 更新订单状态（状态机校验）
     */
    @Transactional
    public Order updateStatus(String orderId, OrderStatus targetStatus, String errorMsg) {
        Order order = getOrderByOrderId(orderId);
        OrderStatus currentStatus = OrderStatus.fromCode(order.getStatus());

        // 状态机校验
        stateMachine.validateTransit(currentStatus, targetStatus);

        order.setStatus(targetStatus.getCode());
        order.setUpdateTime(LocalDateTime.now());
        if (errorMsg != null) {
            order.setErrorMessage(errorMsg);
        }
        orderMapper.updateById(order);

        log.info("订单状态流转: {} {} → {}", orderId, currentStatus.getCode(), targetStatus.getCode());
        return order;
    }

    /**
     * 根据订单ID查询
     */
    public Order getOrderByOrderId(String orderId) {
        Order order = orderMapper.selectByOrderId(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在: " + orderId);
        }
        return order;
    }

    /**
     * 查询策略订单
     */
    public List<Order> getOrdersByStrategy(String strategyId) {
        return orderMapper.selectByStrategy(strategyId);
    }

    /**
     * 查询所有活跃订单
     */
    public List<Order> getActiveOrders() {
        return orderMapper.selectActiveOrders();
    }

    private static String sha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
