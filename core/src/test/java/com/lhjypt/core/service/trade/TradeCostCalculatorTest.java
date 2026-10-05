package com.lhjypt.core.service.trade;

import com.lhjypt.core.common.exception.TradeRejectedException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TradeCostCalculatorTest {

    private final TradeCostCalculator calculator = new TradeCostCalculator(
            new BigDecimal("0.1"), new BigDecimal("0.0003"));

    @Test
    void openOrderReservesMarginAndCommission() {
        TradeCostCalculator.TradeCost cost = calculator.calculate(
                "OPEN", new BigDecimal("3500"), new BigDecimal("2"));

        assertEquals(0, cost.turnover().compareTo(new BigDecimal("7000")));
        assertEquals(0, cost.margin().compareTo(new BigDecimal("700")));
        assertEquals(0, cost.commission().compareTo(new BigDecimal("2.1")));
    }

    @Test
    void closeOrderOnlyChargesCommission() {
        TradeCostCalculator.TradeCost cost = calculator.calculate(
                "CLOSE", new BigDecimal("3500"), new BigDecimal("2"));

        assertEquals(0, cost.margin().compareTo(BigDecimal.ZERO));
        assertEquals(0, cost.commission().compareTo(new BigDecimal("2.1")));
    }

    @Test
    void invalidPriceOrQuantityIsRejected() {
        assertThrows(TradeRejectedException.class,
                () -> calculator.calculate("OPEN", null, BigDecimal.ONE));
        assertThrows(TradeRejectedException.class,
                () -> calculator.calculate("OPEN", BigDecimal.ONE, BigDecimal.ZERO));
    }
}
