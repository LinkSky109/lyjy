package com.lhjypt.core.service.trade.impl;

import com.lhjypt.core.model.trade.CTPConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CTPGatewayAdapterTest {

    @Test
    void liveAdapterIsExplicitlyUnavailable() {
        CTPGatewayAdapter adapter = new CTPGatewayAdapter();

        assertFalse(adapter.isAvailable());
        assertThrows(UnsupportedOperationException.class,
                () -> adapter.connect(new CTPConfig()));
        assertThrows(UnsupportedOperationException.class,
                () -> adapter.submitOrder(null));
        assertThrows(UnsupportedOperationException.class,
                () -> adapter.cancelOrder("EX", "rb2410"));
        assertThrows(UnsupportedOperationException.class,
                () -> adapter.queryOrder("EX"));
        assertThrows(UnsupportedOperationException.class,
                () -> adapter.queryTrades());
        assertThrows(UnsupportedOperationException.class,
                () -> adapter.queryPositions());
        assertThrows(UnsupportedOperationException.class,
                () -> adapter.queryAccount());
    }
}
