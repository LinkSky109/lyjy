package com.lhjypt.core.service.risk;

import com.lhjypt.core.common.exception.TradeRejectedException;
import com.lhjypt.core.mapper.FundAccountMapper;
import com.lhjypt.core.mapper.PositionMapper;
import com.lhjypt.core.model.FundAccount;
import com.lhjypt.core.model.Position;
import com.lhjypt.core.model.market.TickData;
import com.lhjypt.core.model.trade.TradeRequest;
import com.lhjypt.core.service.market.MarketDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 从持久化和行情服务构建真实事前风控上下文。
 */
@Service
@RequiredArgsConstructor
public class TradingRiskContextFactory {

    private final PositionMapper positionMapper;
    private final FundAccountMapper fundAccountMapper;
    private final MarketDataService marketDataService;
    private final PreTradeRiskService preTradeRiskService;

    public RiskRule.RiskContext create(TradeRequest request) {
        FundAccount account = fundAccountMapper.selectByAccountId(request.getAccountId());
        if (account == null) {
            throw new TradeRejectedException(
                    "ACCOUNT_NOT_FOUND", "账户不存在: " + request.getAccountId());
        }

        return new RiskRule.RiskContext() {
            @Override
            public BigDecimal getCurrentPosition(String symbol) {
                List<Position> positions = positionMapper.selectByAccountAndStrategy(
                        request.getAccountId(), request.getStrategyId());
                return positions.stream()
                        .filter(p -> symbol.equals(p.getSymbol()))
                        .map(TradingRiskContextFactory::signedQuantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }

            @Override
            public BigDecimal getAvailableCash() {
                return account.getAvailableCash();
            }

            @Override
            public BigDecimal getTotalAsset() {
                return account.getTotalAsset();
            }

            @Override
            public int getOrderCount(String strategyId, int windowSeconds) {
                return preTradeRiskService.countOrders(strategyId, windowSeconds);
            }

            @Override
            public BigDecimal getBidPrice(String symbol) {
                TickData tick = marketDataService.getLatestTick(symbol);
                return tick == null ? null : tick.getBidPrice1();
            }

            @Override
            public BigDecimal getAskPrice(String symbol) {
                TickData tick = marketDataService.getLatestTick(symbol);
                return tick == null ? null : tick.getAskPrice1();
            }

            @Override
            public boolean isBlacklisted(String symbol) {
                return preTradeRiskService.getBlacklistRule()
                        .getBlacklist().contains(symbol);
            }
        };
    }

    private static BigDecimal signedQuantity(Position position) {
        BigDecimal quantity = position.getQuantity() == null
                ? BigDecimal.ZERO
                : position.getQuantity();
        return "SHORT".equalsIgnoreCase(position.getDirection())
                ? quantity.negate()
                : quantity;
    }
}
