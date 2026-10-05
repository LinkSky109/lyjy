package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.model.Order;
import com.lhjypt.core.model.order.CreateOrderCommand;
import com.lhjypt.core.service.order.OrderManagementService;
import com.lhjypt.core.service.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * OMS订单管理API
 */
@RestController
@RequestMapping("/api/oms")
@RequiredArgsConstructor
public class OrderManagementController {

    private final OrderManagementService orderService;

    /**
     * 创建订单
     */
    @PostMapping("/orders")
    public Result<Order> createOrder(@RequestBody CreateOrderCommand command) {
        return Result.success(orderService.createOrder(command));
    }

    /**
     * 查询订单详情
     */
    @GetMapping("/orders/{orderId}")
    public Result<Order> getOrder(@PathVariable String orderId) {
        return Result.success(orderService.getOrderByOrderId(orderId));
    }

    /**
     * 更新订单状态
     */
    @PutMapping("/orders/{orderId}/status")
    public Result<Order> updateStatus(@PathVariable String orderId,
                                      @RequestParam String status,
                                      @RequestParam(required = false) String errorMsg) {
        return Result.success(orderService.updateStatus(
                orderId, OrderStatus.fromCode(status), errorMsg));
    }

    /**
     * 查询策略订单
     */
    @GetMapping("/orders/strategy/{strategyId}")
    public Result<List<Order>> getByStrategy(@PathVariable String strategyId) {
        return Result.success(orderService.getOrdersByStrategy(strategyId));
    }

    /**
     * 查询活跃订单
     */
    @GetMapping("/orders/active")
    public Result<List<Order>> getActiveOrders() {
        return Result.success(orderService.getActiveOrders());
    }
}
