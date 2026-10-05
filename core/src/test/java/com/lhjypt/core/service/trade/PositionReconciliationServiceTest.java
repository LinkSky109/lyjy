package com.lhjypt.core.service.trade;

import com.lhjypt.core.mapper.PositionMapper;
import com.lhjypt.core.mapper.PositionReconciliationMapper;
import com.lhjypt.core.model.Position;
import com.lhjypt.core.model.trade.PositionDetail;
import com.lhjypt.core.model.trade.PositionReconciliation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PositionReconciliationServiceTest {

    @Test
    void marksPositionMismatchAndPersistsDifference() {
        PositionMapper positionMapper = mock(PositionMapper.class);
        PositionReconciliationMapper reconciliationMapper =
                mock(PositionReconciliationMapper.class);
        TradeExecutionService executionService = mock(TradeExecutionService.class);
        PositionReconciliationService service = new PositionReconciliationService(
                positionMapper, reconciliationMapper, executionService);

        when(executionService.isAuthenticated()).thenReturn(true);
        when(positionMapper.selectByAccount("DEFAULT-001"))
                .thenReturn(List.of(position("2")));
        when(executionService.queryPositions())
                .thenReturn(List.of(positionDetail("1.5")));

        List<PositionReconciliation> result = service.reconcile("DEFAULT-001", null);

        assertEquals(1, result.size());
        assertEquals("MISMATCHED", result.get(0).getStatus());
        assertEquals(0, result.get(0).getDifference().compareTo(new BigDecimal("0.5")));
        verify(reconciliationMapper).insert(any(PositionReconciliation.class));
    }

    @Test
    void marksPositionMatchedWithinTolerance() {
        PositionMapper positionMapper = mock(PositionMapper.class);
        PositionReconciliationMapper reconciliationMapper =
                mock(PositionReconciliationMapper.class);
        TradeExecutionService executionService = mock(TradeExecutionService.class);
        PositionReconciliationService service = new PositionReconciliationService(
                positionMapper, reconciliationMapper, executionService);

        when(executionService.isAuthenticated()).thenReturn(true);
        when(positionMapper.selectByAccount("DEFAULT-001"))
                .thenReturn(List.of(position("2")));
        when(executionService.queryPositions())
                .thenReturn(List.of(positionDetail("2")));

        List<PositionReconciliation> result = service.reconcile("DEFAULT-001", null);

        assertEquals("MATCHED", result.get(0).getStatus());
    }

    private Position position(String quantity) {
        Position position = new Position();
        position.setSymbol("rb2410");
        position.setDirection("LONG");
        position.setQuantity(new BigDecimal(quantity));
        return position;
    }

    private PositionDetail positionDetail(String quantity) {
        PositionDetail position = new PositionDetail();
        position.setSymbol("rb2410");
        position.setDirection("LONG");
        position.setTotalVolume(new BigDecimal(quantity));
        return position;
    }
}
