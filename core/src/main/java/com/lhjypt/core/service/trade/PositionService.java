package com.lhjypt.core.service.trade;

import com.lhjypt.core.mapper.PositionMapper;
import com.lhjypt.core.model.Order;
import com.lhjypt.core.model.Position;
import com.lhjypt.core.model.trade.TradeRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 成交驱动的持仓持久化。
 */
@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionMapper positionMapper;

    @Transactional
    public BigDecimal applyFill(Order order, TradeRecord trade) {
        String side = resolveSide(trade.getDirection(), trade.getOffsetFlag());
        Position position = positionMapper.selectUnique(
                order.getAccountId(), order.getStrategyId(), order.getSymbol(), side);
        if (position == null) {
            position = newPosition(order, side, trade.getPrice());
        }

        BigDecimal quantity = safe(position.getQuantity());
        BigDecimal available = safe(position.getAvailableQuantity());
        BigDecimal fillQuantity = trade.getQuantity();

        if ("OPEN".equalsIgnoreCase(trade.getOffsetFlag())) {
            BigDecimal newQuantity = quantity.add(fillQuantity);
            BigDecimal oldAmount = quantity.multiply(safe(position.getAvgCost()));
            BigDecimal fillAmount = fillQuantity.multiply(trade.getPrice());
            BigDecimal newAvgCost = oldAmount.add(fillAmount)
                    .divide(newQuantity, 8, RoundingMode.HALF_UP);
            position.setQuantity(newQuantity);
            position.setAvailableQuantity(available.add(fillQuantity));
            position.setAvgCost(newAvgCost);
            persist(position);
            return BigDecimal.ZERO;
        }

        if (available.compareTo(fillQuantity) < 0) {
            throw new IllegalStateException(
                    "可平持仓不足: " + side + " 可用 " + available + " 需要 " + fillQuantity);
        }

        BigDecimal realizedPnl = calculateRealizedPnl(
                side, safe(position.getAvgCost()), trade.getPrice(), fillQuantity);
        BigDecimal newQuantity = quantity.subtract(fillQuantity);
        position.setQuantity(newQuantity);
        position.setAvailableQuantity(available.subtract(fillQuantity));
        if (newQuantity.compareTo(BigDecimal.ZERO) == 0) {
            position.setAvgCost(BigDecimal.ZERO);
        }
        persist(position);
        return realizedPnl;
    }

    private Position newPosition(Order order, String side, BigDecimal price) {
        Position position = new Position();
        position.setAccountId(order.getAccountId());
        position.setStrategyId(order.getStrategyId());
        position.setSymbol(order.getSymbol());
        position.setDirection(side);
        position.setQuantity(BigDecimal.ZERO);
        position.setAvailableQuantity(BigDecimal.ZERO);
        position.setAvgCost(price);
        position.setUnrealizedPnl(BigDecimal.ZERO);
        position.setCreateTime(LocalDateTime.now());
        position.setUpdateTime(LocalDateTime.now());
        position.setDeleted(0);
        return position;
    }

    private void persist(Position position) {
        position.setUpdateTime(LocalDateTime.now());
        if (position.getId() == null) {
            positionMapper.insert(position);
        } else {
            positionMapper.updateById(position);
        }
    }

    private static String resolveSide(String direction, String offsetFlag) {
        boolean close = !"OPEN".equalsIgnoreCase(offsetFlag);
        if (close) {
            return "SELL".equalsIgnoreCase(direction) ? "LONG" : "SHORT";
        }
        return "BUY".equalsIgnoreCase(direction) ? "LONG" : "SHORT";
    }

    private static BigDecimal calculateRealizedPnl(String side, BigDecimal avgCost,
                                                   BigDecimal fillPrice, BigDecimal quantity) {
        BigDecimal difference = "LONG".equals(side)
                ? fillPrice.subtract(avgCost)
                : avgCost.subtract(fillPrice);
        return difference.multiply(quantity).setScale(8, RoundingMode.HALF_UP);
    }

    private static BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
