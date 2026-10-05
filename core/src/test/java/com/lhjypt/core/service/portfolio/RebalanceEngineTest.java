package com.lhjypt.core.service.portfolio;

import com.lhjypt.core.model.market.TickData;
import com.lhjypt.core.model.portfolio.Portfolio;
import com.lhjypt.core.model.portfolio.PortfolioPosition;
import com.lhjypt.core.model.portfolio.RebalanceInstruction;
import com.lhjypt.core.model.portfolio.RebalancePreview;
import com.lhjypt.core.service.market.MarketDataService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RebalanceEngineTest {

    @Test
    void buildsBuyAndSellInstructionsWithCostEstimate() {
        MarketDataService market = mock(MarketDataService.class);
        RebalanceEngine engine = new RebalanceEngine(market);
        Portfolio portfolio = portfolio("""
                {"maxSingleWeight":0.95,"maxLeverage":2.0,
                 "priceMap":{"rb2410":3500,"cu2410":72000}}
                """);

        RebalancePreview preview = engine.build(portfolio, List.of(
                position("rb2410", "LONG", "2", "0", null),
                position("cu2410", "LONG", "1", "0", null)));

        assertEquals(2, preview.getInstructions().size());
        assertEquals(0, preview.getCostEst().compareTo(new BigDecimal("79000")));
        assertTrue(preview.getInstructions().stream()
                .allMatch(item -> RebalanceInstruction.PENDING.equals(item.getStatus())));
    }

    @Test
    void rejectsSingleWeightAndMissingPrice() {
        MarketDataService market = mock(MarketDataService.class);
        when(market.getLatestTick("unknown")).thenReturn(null);
        RebalanceEngine engine = new RebalanceEngine(market);
        Portfolio portfolio = portfolio("""
                {"maxSingleWeight":0.5,"maxLeverage":3.0,
                 "priceMap":{"dominant":100}}
                """);

        RebalancePreview preview = engine.build(portfolio, List.of(
                position("dominant", "LONG", "10", "0", null),
                position("minor", "LONG", "1", "0", null),
                position("unknown", "LONG", "1", "0", null)));

        assertTrue(preview.getInstructions().stream()
                .anyMatch(item -> "SINGLE_WEIGHT_EXCEEDED".equals(item.getReason())));
        assertTrue(preview.getInstructions().stream()
                .anyMatch(item -> "PRICE_MISSING".equals(item.getReason())));
    }

    @Test
    void leverageLimitRejectsPendingInstructions() {
        MarketDataService market = mock(MarketDataService.class);
        RebalanceEngine engine = new RebalanceEngine(market);
        Portfolio portfolio = portfolio("""
                {"maxSingleWeight":1.0,"maxLeverage":0.5,
                 "priceMap":{"rb2410":3500}}
                """);

        RebalancePreview preview = engine.build(portfolio, List.of(
                position("rb2410", "LONG", "10", "1", null)));

        assertEquals(RebalanceInstruction.REJECTED,
                preview.getInstructions().get(0).getStatus());
        assertEquals("LEVERAGE_EXCEEDED",
                preview.getInstructions().get(0).getReason());
    }

    private static Portfolio portfolio(String config) {
        Portfolio portfolio = new Portfolio();
        portfolio.setPortfolioId("PF-1");
        portfolio.setConfigJson(config);
        return portfolio;
    }

    private static PortfolioPosition position(String symbol, String direction,
                                              String target, String current,
                                              BigDecimal weightLimit) {
        PortfolioPosition position = new PortfolioPosition();
        position.setPortfolioId("PF-1");
        position.setSymbol(symbol);
        position.setDirection(direction);
        position.setTargetQty(new BigDecimal(target));
        position.setCurQty(new BigDecimal(current));
        position.setWeightLimit(weightLimit);
        return position;
    }
}
