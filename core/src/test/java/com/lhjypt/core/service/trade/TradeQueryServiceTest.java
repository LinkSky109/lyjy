package com.lhjypt.core.service.trade;

import com.lhjypt.core.mapper.FundAccountMapper;
import com.lhjypt.core.mapper.PositionMapper;
import com.lhjypt.core.model.FundAccount;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeQueryServiceTest {

    @Test
    void queriesPositionsByAccountAndStrategy() {
        PositionMapper positionMapper = mock(PositionMapper.class);
        FundAccountMapper accountMapper = mock(FundAccountMapper.class);
        TradeQueryService service = new TradeQueryService(positionMapper, accountMapper);
        when(positionMapper.selectByAccountAndStrategy("A", "S")).thenReturn(List.of());

        service.positions("A", "S");

        verify(positionMapper).selectByAccountAndStrategy("A", "S");
    }

    @Test
    void returnsAccountOrRejectsMissingAccount() {
        PositionMapper positionMapper = mock(PositionMapper.class);
        FundAccountMapper accountMapper = mock(FundAccountMapper.class);
        TradeQueryService service = new TradeQueryService(positionMapper, accountMapper);
        FundAccount account = new FundAccount();
        when(accountMapper.selectByAccountId("A")).thenReturn(account);

        assertEquals(account, service.account("A"));
        assertThrows(IllegalArgumentException.class, () -> service.account("missing"));
        assertThrows(IllegalArgumentException.class, () -> service.positions("", null));
    }
}
