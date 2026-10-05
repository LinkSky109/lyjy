package com.lhjypt.core.service.fund;

import com.lhjypt.core.mapper.OrderFundReservationMapper;
import com.lhjypt.core.model.Order;
import com.lhjypt.core.model.fund.OrderFundReservation;
import com.lhjypt.core.model.trade.TradeRequest;
import com.lhjypt.core.service.trade.TradeCostCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单级资金冻结、消耗和释放。
 */
@Service
@RequiredArgsConstructor
public class FundReservationService {

    private final OrderFundReservationMapper reservationMapper;
    private final FundManagementService fundManagementService;

    @Transactional
    public OrderFundReservation reserve(Order order, TradeRequest request,
                                        TradeCostCalculator.TradeCost cost) {
        OrderFundReservation existing = reservationMapper.selectByOrderId(order.getOrderId());
        if (existing != null) {
            return existing;
        }

        BigDecimal reserveAmount = cost.reserveAmount();
        fundManagementService.freezeCash(
                order.getAccountId(), reserveAmount, order.getOrderId(), order.getSymbol());

        OrderFundReservation reservation = new OrderFundReservation();
        reservation.setOrderId(order.getOrderId());
        reservation.setAccountId(order.getAccountId());
        reservation.setReservedMargin(cost.margin());
        reservation.setReservedCommission(cost.commission());
        reservation.setConsumedMargin(BigDecimal.ZERO);
        reservation.setConsumedCommission(BigDecimal.ZERO);
        reservation.setReleasedAmount(BigDecimal.ZERO);
        reservation.setConsumedAmount(BigDecimal.ZERO);
        reservation.setStatus("RESERVED");
        reservation.setCreateTime(LocalDateTime.now());
        reservation.setUpdateTime(LocalDateTime.now());
        reservationMapper.insert(reservation);
        return reservation;
    }

    /**
     * 成交后消耗本次成交对应的保证金和手续费。
     */
    @Transactional
    public OrderFundReservation consumeFill(String orderId, BigDecimal actualMargin,
                                            BigDecimal actualCommission) {
        OrderFundReservation reservation = requireReservation(orderId);
        if ("RELEASED".equals(reservation.getStatus())
                || "CONSUMED".equals(reservation.getStatus())) {
            return reservation;
        }

        BigDecimal remainingMargin = remaining(
                reservation.getReservedMargin(), reservation.getConsumedMargin());
        BigDecimal remainingCommission = remaining(
                reservation.getReservedCommission(), reservation.getConsumedCommission());

        BigDecimal marginToConsume = min(remainingMargin, safe(actualMargin));
        BigDecimal commissionToConsume = min(remainingCommission, safe(actualCommission));

        if (commissionToConsume.compareTo(BigDecimal.ZERO) > 0) {
            fundManagementService.releaseCash(
                    reservation.getAccountId(), commissionToConsume,
                    orderId + "-COMMISSION", null);
        }
        if (marginToConsume.compareTo(BigDecimal.ZERO) > 0
                || safe(actualCommission).compareTo(BigDecimal.ZERO) > 0) {
            fundManagementService.settleMarginAndCommission(
                    reservation.getAccountId(), marginToConsume,
                    safe(actualCommission), orderId, null);
        }

        reservation.setConsumedMargin(
                safe(reservation.getConsumedMargin()).add(marginToConsume));
        reservation.setConsumedCommission(
                safe(reservation.getConsumedCommission()).add(commissionToConsume));
        reservation.setConsumedAmount(
                reservation.getConsumedMargin().add(reservation.getConsumedCommission()));
        reservation.setStatus(isFullyConsumed(reservation) ? "CONSUMED" : "PARTIAL_CONSUMED");
        reservation.setUpdateTime(LocalDateTime.now());
        reservationMapper.updateById(reservation);
        return reservation;
    }

    /**
     * 订单终止时释放剩余冻结资金。
     */
    @Transactional
    public OrderFundReservation releaseRemaining(String orderId) {
        OrderFundReservation reservation = reservationMapper.selectByOrderId(orderId);
        if (reservation == null || "RELEASED".equals(reservation.getStatus())) {
            return reservation;
        }

        BigDecimal remainingMargin = remaining(
                reservation.getReservedMargin(), reservation.getConsumedMargin());
        BigDecimal remainingCommission = remaining(
                reservation.getReservedCommission(), reservation.getConsumedCommission());
        BigDecimal remainingFrozen = remainingMargin.add(remainingCommission);

        if (remainingFrozen.compareTo(BigDecimal.ZERO) > 0) {
            fundManagementService.releaseCash(
                    reservation.getAccountId(), remainingFrozen,
                    orderId + "-RELEASE", null);
        }

        reservation.setReleasedAmount(
                safe(reservation.getReleasedAmount()).add(remainingFrozen));
        reservation.setStatus("RELEASED");
        reservation.setUpdateTime(LocalDateTime.now());
        reservationMapper.updateById(reservation);
        return reservation;
    }

    public OrderFundReservation getByOrderId(String orderId) {
        return reservationMapper.selectByOrderId(orderId);
    }

    private OrderFundReservation requireReservation(String orderId) {
        OrderFundReservation reservation = reservationMapper.selectByOrderId(orderId);
        if (reservation == null) {
            throw new IllegalStateException("订单资金冻结记录不存在: " + orderId);
        }
        return reservation;
    }

    private static boolean isFullyConsumed(OrderFundReservation reservation) {
        BigDecimal reserved = safe(reservation.getReservedMargin())
                .add(safe(reservation.getReservedCommission()));
        BigDecimal consumed = safe(reservation.getConsumedMargin())
                .add(safe(reservation.getConsumedCommission()));
        return consumed.compareTo(reserved) >= 0;
    }

    private static BigDecimal remaining(BigDecimal reserved, BigDecimal consumed) {
        return safe(reserved).subtract(safe(consumed)).max(BigDecimal.ZERO);
    }

    private static BigDecimal min(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private static BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
