package com.lhjypt.core.service.trade;

import com.lhjypt.core.common.exception.TradeRejectedException;
import com.lhjypt.core.model.trade.TradeRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 订单保证金与手续费估算。
 */
@Component
public class TradeCostCalculator {

    @Value("${lhjypt.fund.margin-ratio:0.1}")
    private BigDecimal marginRatio;

    @Value("${lhjypt.fund.commission-rate:0.0003}")
    private BigDecimal commissionRate;

    public TradeCost estimate(TradeRequest request) {
        if (request.getPrice() == null) {
            throw new TradeRejectedException(
                    "PRICE_REQUIRED", "当前版本暂不支持无参考价的市价单资金冻结");
        }
        if (request.getQuantity() == null
                || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new TradeRejectedException(
                    "INVALID_QUANTITY", "委托数量必须大于0");
        }

        return calculate(request.getOffsetFlag(), request.getPrice(), request.getQuantity());
    }

    public TradeCost calculate(String offsetFlag, BigDecimal price, BigDecimal quantity) {
        if (price == null || quantity == null
                || price.compareTo(BigDecimal.ZERO) <= 0
                || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TradeRejectedException(
                    "VALIDATION_FAILED", "成本和保证金计算需要有效价格与数量");
        }

        BigDecimal turnover = price.multiply(quantity);
        BigDecimal margin = "OPEN".equalsIgnoreCase(offsetFlag)
                ? turnover.multiply(marginRatio).setScale(8, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        BigDecimal commission = turnover.multiply(commissionRate)
                .setScale(8, RoundingMode.HALF_UP);
        return new TradeCost(turnover, margin, commission);
    }

    public record TradeCost(BigDecimal turnover, BigDecimal margin, BigDecimal commission) {

        public BigDecimal reserveAmount() {
            return margin.add(commission).setScale(8, RoundingMode.HALF_UP);
        }
    }
}
