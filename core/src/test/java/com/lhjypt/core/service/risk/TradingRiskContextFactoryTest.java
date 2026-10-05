package com.lhjypt.core.service.risk;

import com.lhjypt.core.mapper.FundAccountMapper;
import com.lhjypt.core.mapper.PositionMapper;
import com.lhjypt.core.model.FundAccount;
import com.lhjypt.core.model.Position;
import com.lhjypt.core.model.market.TickData;
import com.lhjypt.core.model.trade.TradeRequest;
import com.lhjypt.core.service.market.MarketDataService;
import com.lhjypt.core.service.risk.impl.BlacklistRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradingRiskContextFactoryTest {

    @Test
    void buildsContextFromPersistedPositionsFundsAndMarketData() {
        PositionMapper positionMapper = mock(PositionMapper.class);
        FundAccountMapper accountMapper = mock(FundAccountMapper.class);
        MarketDataService marketDataService = mock(MarketDataService.class);
        PreTradeRiskService riskService = mock(PreTradeRiskService.class);
        BlacklistRule blacklistRule = mock(BlacklistRule.class);

        TradingRiskContextFactory factory = new TradingRiskContextFactory(
                positionMapper, accountMapper, marketDataService, riskService);

        TradeRequest request = new TradeRequest();
        request.setAccountId("DEFAULT-001");
        request.setStrategyId("S-001");
        request.setSymbol("rb2410");

        FundAccount account = new FundAccount();
        account.setAvailableCash(new BigDecimal("100000"));
        account.setTotalAsset(new BigDecimal("200000"));
        when(accountMapper.selectByAccountId("DEFAULT-001")).thenReturn(account);

        Position longPosition = new Position();
        longPosition.setSymbol("rb2410");
        longPosition.setDirection("LONG");
        longPosition.setQuantity(new BigDecimal("3"));
        Position shortPosition = new Position();
        shortPosition.setSymbol("rb2410");
        shortPosition.setDirection("SHORT");
        shortPosition.setQuantity(new BigDecimal("1"));
        when(positionMapper.selectByAccountAndStrategy("DEFAULT-001", "S-001"))
                .thenReturn(List.of(longPosition, shortPosition));

        TickData tick = new TickData();
        tick.setBidPrice1(new BigDecimal("3499"));
        tick.setAskPrice1(new BigDecimal("3501"));
        when(marketDataService.getLatestTick("rb2410")).thenReturn(tick);
        when(riskService.countOrders("S-001", 60)).thenReturn(7);
        when(riskService.getBlacklistRule()).thenReturn(blacklistRule);
        when(blacklistRule.getBlacklist()).thenReturn(Set.of("rb2410"));

        RiskRule.RiskContext context = factory.create(request);

        assertEquals(0, context.getCurrentPosition("rb2410").compareTo(new BigDecimal("2")));
        assertEquals(0, context.getAvailableCash().compareTo(new BigDecimal("100000")));
        assertEquals(0, context.getTotalAsset().compareTo(new BigDecimal("200000")));
        assertEquals(7, context.getOrderCount("S-001", 60));
        assertEquals(0, context.getBidPrice("rb2410").compareTo(new BigDecimal("3499")));
        assertEquals(0, context.getAskPrice("rb2410").compareTo(new BigDecimal("3501")));
        assertTrue(context.isBlacklisted("rb2410"));
        assertFalse(context.isBlacklisted("cu2410"));
    }
}
