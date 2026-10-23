package com.lhjypt.core.service.portfolio;

import com.lhjypt.core.mapper.PortfolioMapper;
import com.lhjypt.core.mapper.PortfolioNavMapper;
import com.lhjypt.core.mapper.TradeRecordMapper;
import com.lhjypt.core.model.FundAccount;
import com.lhjypt.core.model.portfolio.Portfolio;
import com.lhjypt.core.model.portfolio.PortfolioNav;
import com.lhjypt.core.model.trade.TradeRecord;
import com.lhjypt.core.service.trade.TradeQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioAnalyticsServiceTest {

    private PortfolioMapper portfolioMapper;
    private PortfolioNavMapper navMapper;
    private TradeRecordMapper tradeRecordMapper;
    private TradeQueryService tradeQueryService;
    private PortfolioAnalyticsService service;

    @BeforeEach
    void setUp() {
        portfolioMapper = mock(PortfolioMapper.class);
        navMapper = mock(PortfolioNavMapper.class);
        tradeRecordMapper = mock(TradeRecordMapper.class);
        tradeQueryService = mock(TradeQueryService.class);
        service = new PortfolioAnalyticsService(portfolioMapper, navMapper,
                tradeRecordMapper, tradeQueryService);
        when(portfolioMapper.selectByPortfolioId("PF-1")).thenReturn(portfolio());
    }

    @Test
    @DisplayName("快照按配置账户权益记录当日净值")
    void snapshotUsesAccountEquity() {
        FundAccount account = new FundAccount();
        account.setTotalAsset(new BigDecimal("100000"));
        account.setAvailableCash(new BigDecimal("80000"));
        account.setFrozenCash(new BigDecimal("5000"));
        when(tradeQueryService.account("ACC-1")).thenReturn(account);
        when(navMapper.selectByDate("PF-1", LocalDate.now())).thenReturn(null);

        PortfolioNav nav = service.snapshot("PF-1");

        assertThat(nav.getNav()).isEqualByComparingTo("100000");
        assertThat(nav.getEquity()).isEqualByComparingTo("100000");
        assertThat(nav.getCash()).isEqualByComparingTo("85000");
        verify(navMapper).insert(any(PortfolioNav.class));
    }

    @Test
    @DisplayName("同日重复快照执行更新而非新增")
    void snapshotUpdatesSameDay() {
        FundAccount account = new FundAccount();
        account.setTotalAsset(new BigDecimal("90000"));
        account.setAvailableCash(new BigDecimal("90000"));
        account.setFrozenCash(BigDecimal.ZERO);
        when(tradeQueryService.account("ACC-1")).thenReturn(account);
        PortfolioNav existing = new PortfolioNav();
        existing.setId(8L);
        existing.setPortfolioId("PF-1");
        when(navMapper.selectByDate("PF-1", LocalDate.now())).thenReturn(existing);

        service.snapshot("PF-1");

        verify(navMapper).updateById(existing);
        assertThat(existing.getEquity()).isEqualByComparingTo("90000");
    }

    @Test
    @DisplayName("净值区间默认近 30 天并校验组合存在")
    void navDefaultsToThirtyDays() {
        when(navMapper.selectRange(any(), any(), any())).thenReturn(List.of());

        service.nav("PF-1", null, null);

        verify(navMapper).selectRange("PF-1", LocalDate.now().minusDays(30), LocalDate.now());
        assertThatThrownBy(() -> service.nav("PF-X", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("策略贡献按已实现盈亏分组并计算占比")
    void attributionGroupsByStrategy() {
        when(tradeRecordMapper.selectByAccountAndTimeRange(any(), any(), any())).thenReturn(List.of(
                trade("S-1", "100", "1000"),
                trade("S-1", "20", "500"),
                trade("S-2", "-40", "800"),
                trade(null, "10", "100")));

        Map<String, Object> result = service.attribution("PF-1",
                LocalDateTime.now().minusDays(30), LocalDateTime.now());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");

        assertThat(result.get("totalPnl")).isEqualTo(new BigDecimal("90"));
        assertThat(items).hasSize(3);
        assertThat(items.get(0).get("strategyId")).isEqualTo("S-1");
        assertThat(items.get(0).get("realizedPnl")).isEqualTo(new BigDecimal("120"));
        assertThat(items.get(0).get("contribution")).isEqualTo(new BigDecimal("133.3"));
        assertThat(items).anySatisfy(row -> assertThat(row.get("strategyId")).isEqualTo("UNASSIGNED"));
    }

    @Test
    @DisplayName("无成交时贡献为空且总盈亏为零")
    void attributionHandlesEmptyTrades() {
        when(tradeRecordMapper.selectByAccountAndTimeRange(any(), any(), any())).thenReturn(List.of());

        Map<String, Object> result = service.attribution("PF-1", null, null);

        assertThat(result.get("totalPnl")).isEqualTo(BigDecimal.ZERO);
        assertThat((List<?>) result.get("items")).isEmpty();
    }

    private static Portfolio portfolio() {
        Portfolio portfolio = new Portfolio();
        portfolio.setPortfolioId("PF-1");
        portfolio.setConfigJson("{\"accountId\":\"ACC-1\"}");
        return portfolio;
    }

    private static TradeRecord trade(String strategyId, String pnl, String turnover) {
        TradeRecord trade = new TradeRecord();
        trade.setStrategyId(strategyId);
        trade.setRealizedPnl(new BigDecimal(pnl));
        trade.setTurnover(new BigDecimal(turnover));
        return trade;
    }
}
