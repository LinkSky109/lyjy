package com.lhjypt.core.service.risk.impl;

import com.lhjypt.core.model.risk.RiskCheckResult;
import com.lhjypt.core.model.trade.TradeRequest;
import com.lhjypt.core.service.risk.RiskRule;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 自成交防护规则
 * 防止同一账户的买单和卖单相互成交（交易所认定为违规自成交）
 * 检查委托价格是否会与自身反向挂单成交
 */
@Component
public class SelfTradePreventionRule implements RiskRule {

    private boolean enabled = true;

    @Override
    public String getCode() { return "SELF_TRADE"; }

    @Override
    public String getName() { return "自成交防护"; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public void check(TradeRequest request, RiskContext context, RiskCheckResult result) {
        if (!enabled || request.getPrice() == null) return;

        BigDecimal price = request.getPrice();

        if ("BUY".equals(request.getDirection())) {
            // 买单价格 >= 自身卖一价，可能自成交
            BigDecimal askPrice = context.getAskPrice(request.getSymbol());
            if (askPrice != null && price.compareTo(askPrice) >= 0) {
                result.addViolation(getCode(), getName(),
                        String.format("买价 %s ≥ 自身卖价 %s，存在自成交风险",
                                price, askPrice),
                        "REJECT");
            }
        } else if ("SELL".equals(request.getDirection())) {
            // 卖单价格 <= 自身买一价，可能自成交
            BigDecimal bidPrice = context.getBidPrice(request.getSymbol());
            if (bidPrice != null && price.compareTo(bidPrice) <= 0) {
                result.addViolation(getCode(), getName(),
                        String.format("卖价 %s ≤ 自身买价 %s，存在自成交风险",
                                price, bidPrice),
                        "REJECT");
            }
        }
    }
}
