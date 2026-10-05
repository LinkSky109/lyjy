package com.lhjypt.core.service.portfolio;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lhjypt.core.model.market.TickData;
import com.lhjypt.core.model.portfolio.Portfolio;
import com.lhjypt.core.model.portfolio.PortfolioPosition;
import com.lhjypt.core.model.portfolio.RebalanceInstruction;
import com.lhjypt.core.model.portfolio.RebalancePreview;
import com.lhjypt.core.service.market.MarketDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 组合再平衡预览引擎。
 */
@Service
@RequiredArgsConstructor
public class RebalanceEngine {

    private final MarketDataService marketDataService;

    public RebalancePreview build(Portfolio portfolio,
                                  List<PortfolioPosition> positions) {
        JSONObject config = parse(portfolio.getConfigJson());
        BigDecimal maxSingleWeight = decimal(config, "maxSingleWeight", "1.0");
        BigDecimal maxLeverage = decimal(config, "maxLeverage", "1.0");
        JSONObject priceMap = config.getJSONObject("priceMap");

        RebalancePreview preview = new RebalancePreview();
        BigDecimal totalTargetValue = BigDecimal.ZERO;
        BigDecimal totalCurrentValue = BigDecimal.ZERO;

        for (PortfolioPosition position : positions) {
            BigDecimal price = resolvePrice(position.getSymbol(), priceMap);
            if (price != null) {
                totalTargetValue = totalTargetValue.add(
                        safe(position.getTargetQty()).abs().multiply(price));
                totalCurrentValue = totalCurrentValue.add(
                        safe(position.getCurQty()).abs().multiply(price));
            }
        }

        int index = 0;
        for (PortfolioPosition position : positions) {
            BigDecimal delta = safe(position.getTargetQty())
                    .subtract(safe(position.getCurQty()));
            if (delta.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            BigDecimal price = resolvePrice(position.getSymbol(), priceMap);
            RebalanceInstruction instruction = new RebalanceInstruction();
            instruction.setIndex(index++);
            instruction.setSymbol(position.getSymbol());
            instruction.setExchange(position.getExchange());
            instruction.setQuantity(delta.abs());
            instruction.setPrice(price);
            instruction.setStatus(RebalanceInstruction.PENDING);

            boolean isLong = !"SHORT".equalsIgnoreCase(position.getDirection());
            if (delta.compareTo(BigDecimal.ZERO) > 0) {
                instruction.setDirection(isLong ? "BUY" : "SELL");
                instruction.setOffsetFlag("OPEN");
            } else {
                instruction.setDirection(isLong ? "SELL" : "BUY");
                instruction.setOffsetFlag("CLOSE");
            }

            if (price == null) {
                reject(instruction, "PRICE_MISSING");
            } else {
                instruction.setEstimatedCost(delta.abs().multiply(price)
                        .setScale(8, RoundingMode.HALF_UP));
                BigDecimal targetValue = safe(position.getTargetQty()).abs().multiply(price);
                if (totalTargetValue.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal weight = targetValue.divide(
                            totalTargetValue, 8, RoundingMode.HALF_UP);
                    if (weight.compareTo(maxSingleWeight) > 0) {
                        reject(instruction, "SINGLE_WEIGHT_EXCEEDED");
                    } else if (position.getWeightLimit() != null
                            && weight.compareTo(position.getWeightLimit()) > 0) {
                        reject(instruction, "WEIGHT_LIMIT_EXCEEDED");
                    }
                }
            }
            preview.getInstructions().add(instruction);
        }

        BigDecimal leverage = totalCurrentValue.compareTo(BigDecimal.ZERO) > 0
                ? totalTargetValue.divide(totalCurrentValue, 8, RoundingMode.HALF_UP)
                : BigDecimal.ONE;
        if (leverage.compareTo(maxLeverage) > 0) {
            preview.getInstructions().stream()
                    .filter(item -> RebalanceInstruction.PENDING.equals(item.getStatus()))
                    .forEach(item -> reject(item, "LEVERAGE_EXCEEDED"));
        }

        BigDecimal diffTotal = preview.getInstructions().stream()
                .map(RebalanceInstruction::getQuantity)
                .map(RebalanceEngine::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal costEst = preview.getInstructions().stream()
                .filter(item -> RebalanceInstruction.PENDING.equals(item.getStatus()))
                .map(RebalanceInstruction::getEstimatedCost)
                .map(RebalanceEngine::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        preview.setDiffTotal(diffTotal);
        preview.setCostEst(costEst);
        return preview;
    }

    private BigDecimal resolvePrice(String symbol, JSONObject priceMap) {
        if (priceMap != null && priceMap.containsKey(symbol)) {
            return priceMap.getBigDecimal(symbol);
        }
        TickData tick = marketDataService.getLatestTick(symbol);
        return tick == null ? null : tick.getLastPrice();
    }

    private static JSONObject parse(String json) {
        if (json == null || json.isBlank()) {
            return new JSONObject();
        }
        try {
            JSONObject object = JSON.parseObject(json);
            return object == null ? new JSONObject() : object;
        } catch (Exception error) {
            throw new IllegalArgumentException("组合配置 JSON 格式错误: " + error.getMessage());
        }
    }

    private static BigDecimal decimal(JSONObject config, String key, String defaultValue) {
        BigDecimal value = config.getBigDecimal(key);
        return value == null ? new BigDecimal(defaultValue) : value;
    }

    private static void reject(RebalanceInstruction instruction, String reason) {
        instruction.setStatus(RebalanceInstruction.REJECTED);
        instruction.setReason(reason);
    }

    private static BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
