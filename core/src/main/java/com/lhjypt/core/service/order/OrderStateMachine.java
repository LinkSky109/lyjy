package com.lhjypt.core.service.order;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态机
 * 严格定义订单状态的合法流转路径，非法流转抛出异常
 *
 * 状态流转图：
 * PENDING → SUBMITTED → ACCEPTED → PARTIAL_FILLED → FILLED
 *                    ↘ REJECTED
 * SUBMITTED → TIMEOUT
 * ACCEPTED/PARTIAL_FILLED → CANCELED
 */
@Component
public class OrderStateMachine {

    /** 合法流转映射：当前状态 → 可流转的目标状态集合 */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS =
            new EnumMap<>(OrderStatus.class);

    static {
        TRANSITIONS.put(OrderStatus.PENDING, EnumSet.of(
                OrderStatus.SUBMITTED, OrderStatus.REJECTED, OrderStatus.CANCELED));

        TRANSITIONS.put(OrderStatus.SUBMITTED, EnumSet.of(
                OrderStatus.ACCEPTED, OrderStatus.PARTIAL_FILLED,
                OrderStatus.FILLED, OrderStatus.CANCELED,
                OrderStatus.REJECTED, OrderStatus.TIMEOUT));

        TRANSITIONS.put(OrderStatus.ACCEPTED, EnumSet.of(
                OrderStatus.PARTIAL_FILLED, OrderStatus.FILLED,
                OrderStatus.CANCELED, OrderStatus.REJECTED));

        TRANSITIONS.put(OrderStatus.PARTIAL_FILLED, EnumSet.of(
                OrderStatus.PARTIAL_FILLED, OrderStatus.FILLED, OrderStatus.CANCELED));

        // 终态不可流转
        TRANSITIONS.put(OrderStatus.FILLED, EnumSet.noneOf(OrderStatus.class));
        TRANSITIONS.put(OrderStatus.CANCELED, EnumSet.noneOf(OrderStatus.class));
        TRANSITIONS.put(OrderStatus.REJECTED, EnumSet.noneOf(OrderStatus.class));
        TRANSITIONS.put(OrderStatus.TIMEOUT, EnumSet.noneOf(OrderStatus.class));
    }

    /**
     * 判断流转是否合法
     */
    public boolean canTransit(OrderStatus from, OrderStatus to) {
        Set<OrderStatus> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * 校验流转，非法则抛出异常
     */
    public void validateTransit(OrderStatus from, OrderStatus to) {
        if (!canTransit(from, to)) {
            throw new IllegalStateException(
                    String.format("非法订单状态流转: %s → %s", from.getCode(), to.getCode()));
        }
    }

    /**
     * 获取可流转的目标状态
     */
    public Set<OrderStatus> allowedTransitions(OrderStatus from) {
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(OrderStatus.class));
    }
}
