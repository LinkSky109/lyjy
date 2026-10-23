package com.lhjypt.core.service.trade;

import com.lhjypt.core.mapper.FundAccountMapper;
import com.lhjypt.core.mapper.PositionMapper;
import com.lhjypt.core.mapper.TradeRecordMapper;
import com.lhjypt.core.model.FundAccount;
import com.lhjypt.core.model.trade.TradeRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeQueryServiceTest {

    @Test
    void queriesPositionsByAccountAndStrategy() {
        PositionMapper positionMapper = mock(PositionMapper.class);
        FundAccountMapper accountMapper = mock(FundAccountMapper.class);
        TradeQueryService service = new TradeQueryService(positionMapper, accountMapper,
                mock(TradeRecordMapper.class));
        when(positionMapper.selectByAccountAndStrategy("A", "S")).thenReturn(List.of());

        service.positions("A", "S");

        verify(positionMapper).selectByAccountAndStrategy("A", "S");
    }

    @Test
    void pagesTradesWithOptionalFilters() {
        PositionMapper positionMapper = mock(PositionMapper.class);
        FundAccountMapper accountMapper = mock(FundAccountMapper.class);
        TradeRecordMapper tradeMapper = mock(TradeRecordMapper.class);
        TradeQueryService service = new TradeQueryService(positionMapper, accountMapper, tradeMapper);
        TradeRecord record = new TradeRecord();
        record.setTradeId("T-1");
        record.setSymbol("rb2410");
        record.setPrice(new BigDecimal("3500"));
        record.setQuantity(new BigDecimal("2"));
        when(tradeMapper.selectPageFiltered("A", "rb2410", null, null, 0, 20))
                .thenReturn(List.of(record));
        when(tradeMapper.countPageFiltered("A", "rb2410", null, null)).thenReturn(1L);

        Map<String, Object> page = service.pageTrades(" A ", " rb2410 ", null, null, 1, 20);

        assertThat(page.get("items")).isEqualTo(List.of(record));
        assertThat(page.get("total")).isEqualTo(1L);
        assertThat(page.get("page")).isEqualTo(1);
        assertThat(page.get("size")).isEqualTo(20);
    }

    @Test
    void pagesTradesWithoutAccountUsesNullFilters() {
        PositionMapper positionMapper = mock(PositionMapper.class);
        FundAccountMapper accountMapper = mock(FundAccountMapper.class);
        TradeRecordMapper tradeMapper = mock(TradeRecordMapper.class);
        TradeQueryService service = new TradeQueryService(positionMapper, accountMapper, tradeMapper);
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();
        when(tradeMapper.selectPageFiltered(isNull(), isNull(), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of());
        when(tradeMapper.countPageFiltered(isNull(), isNull(), any(), any())).thenReturn(0L);

        Map<String, Object> page = service.pageTrades("", " ", from, to, 2, 10);

        assertThat(page.get("total")).isEqualTo(0L);
        assertThat(page.get("page")).isEqualTo(2);
        assertThat(page.get("size")).isEqualTo(10);
    }

    @Test
    void returnsAccountOrRejectsMissingAccount() {
        PositionMapper positionMapper = mock(PositionMapper.class);
        FundAccountMapper accountMapper = mock(FundAccountMapper.class);
        TradeQueryService service = new TradeQueryService(positionMapper, accountMapper,
                mock(TradeRecordMapper.class));
        FundAccount account = new FundAccount();
        when(accountMapper.selectByAccountId("A")).thenReturn(account);

        assertEquals(account, service.account("A"));
        assertThrows(IllegalArgumentException.class, () -> service.account("missing"));
        assertThrows(IllegalArgumentException.class, () -> service.positions("", null));
    }
}
