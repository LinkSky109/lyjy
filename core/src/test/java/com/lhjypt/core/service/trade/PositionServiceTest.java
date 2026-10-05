package com.lhjypt.core.service.trade;

import com.lhjypt.core.mapper.PositionMapper;
import com.lhjypt.core.model.Order;
import com.lhjypt.core.model.Position;
import com.lhjypt.core.model.trade.TradeRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PositionServiceTest {

    @Test
    void opensPositionAndPersistsWeightedCost() {
        PositionMapper mapper = mock(PositionMapper.class);
        PositionService service = new PositionService(mapper);
        when(mapper.selectUnique("DEFAULT-001", "S-001", "rb2410", "LONG"))
                .thenReturn(null);

        BigDecimal pnl = service.applyFill(order(), trade("BUY", "OPEN", "2", "3500"));

        ArgumentCaptor<Position> captor = ArgumentCaptor.forClass(Position.class);
        verify(mapper).insert(captor.capture());
        assertEquals(0, pnl.compareTo(BigDecimal.ZERO));
        assertEquals(0, captor.getValue().getQuantity().compareTo(new BigDecimal("2")));
        assertEquals(0, captor.getValue().getAvgCost().compareTo(new BigDecimal("3500")));
    }

    @Test
    void addsPositionUsingWeightedAverage() {
        PositionMapper mapper = mock(PositionMapper.class);
        PositionService service = new PositionService(mapper);
        Position existing = position("2", "100");
        when(mapper.selectUnique("DEFAULT-001", "S-001", "rb2410", "LONG"))
                .thenReturn(existing);

        service.applyFill(order(), trade("BUY", "OPEN", "2", "120"));

        assertEquals(0, existing.getQuantity().compareTo(new BigDecimal("4")));
        assertEquals(0, existing.getAvgCost().compareTo(new BigDecimal("110")));
        verify(mapper).updateById(existing);
    }

    @Test
    void closingPositionCalculatesRealizedPnl() {
        PositionMapper mapper = mock(PositionMapper.class);
        PositionService service = new PositionService(mapper);
        Position existing = position("2", "100");
        when(mapper.selectUnique("DEFAULT-001", "S-001", "rb2410", "LONG"))
                .thenReturn(existing);

        BigDecimal pnl = service.applyFill(order(), trade("SELL", "CLOSE", "1", "110"));

        assertEquals(0, pnl.compareTo(new BigDecimal("10")));
        assertEquals(0, existing.getQuantity().compareTo(BigDecimal.ONE));
    }

    @Test
    void rejectsCloseWhenAvailablePositionIsInsufficient() {
        PositionMapper mapper = mock(PositionMapper.class);
        PositionService service = new PositionService(mapper);
        Position existing = position("1", "100");
        when(mapper.selectUnique("DEFAULT-001", "S-001", "rb2410", "LONG"))
                .thenReturn(existing);

        assertThrows(IllegalStateException.class,
                () -> service.applyFill(order(), trade("SELL", "CLOSE", "2", "110")));
    }

    private Order order() {
        Order order = new Order();
        order.setOrderId("ORD-001");
        order.setAccountId("DEFAULT-001");
        order.setStrategyId("S-001");
        order.setSymbol("rb2410");
        return order;
    }

    private TradeRecord trade(String direction, String offset, String quantity, String price) {
        TradeRecord trade = new TradeRecord();
        trade.setTradeId("T-001");
        trade.setDirection(direction);
        trade.setOffsetFlag(offset);
        trade.setQuantity(new BigDecimal(quantity));
        trade.setPrice(new BigDecimal(price));
        return trade;
    }

    private Position position(String quantity, String avgCost) {
        Position position = new Position();
        position.setId(1L);
        position.setAccountId("DEFAULT-001");
        position.setStrategyId("S-001");
        position.setSymbol("rb2410");
        position.setDirection("LONG");
        position.setQuantity(new BigDecimal(quantity));
        position.setAvailableQuantity(new BigDecimal(quantity));
        position.setAvgCost(new BigDecimal(avgCost));
        return position;
    }
}
