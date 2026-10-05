package com.lhjypt.core.service.var;

import com.lhjypt.core.common.auth.UserContext;
import com.lhjypt.core.common.exception.TradeRejectedException;
import com.lhjypt.core.mapper.PortfolioMapper;
import com.lhjypt.core.mapper.PortfolioPositionMapper;
import com.lhjypt.core.mapper.PositionMapper;
import com.lhjypt.core.mapper.VarCalculationMapper;
import com.lhjypt.core.model.Position;
import com.lhjypt.core.model.portfolio.Portfolio;
import com.lhjypt.core.model.portfolio.PortfolioPosition;
import com.lhjypt.core.model.var.VarCalculation;
import com.lhjypt.core.model.var.VarComputeRequest;
import com.lhjypt.core.model.var.VarResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VarServiceTest {

    private VarCalculationMapper varMapper;
    private PortfolioMapper portfolioMapper;
    private PortfolioPositionMapper portfolioPositionMapper;
    private PositionMapper positionMapper;
    private VarMarketDataSource marketDataSource;
    private VarService service;

    @BeforeEach
    void setUp() {
        varMapper = mock(VarCalculationMapper.class);
        portfolioMapper = mock(PortfolioMapper.class);
        portfolioPositionMapper = mock(PortfolioPositionMapper.class);
        positionMapper = mock(PositionMapper.class);
        marketDataSource = mock(VarMarketDataSource.class);
        service = new VarService(varMapper, portfolioMapper,
                portfolioPositionMapper, positionMapper, marketDataSource);
    }

    @Test
    @DisplayName("账户作用域计算并落库，返回样本数与 VaR")
    void computesAndPersistsForAccount() {
        when(positionMapper.selectByAccount("ACC-1"))
                .thenReturn(List.of(position("rb", "LONG", "10")));
        when(marketDataSource.closingPrices(anyString(), anyInt()))
                .thenReturn(alternatingSeries());

        VarResult result = service.compute(request("ACCOUNT", "ACC-1"),
                UserContext.of("risk-1", "RISK_OFFICER", null));

        assertThat(result.getCalcId()).startsWith("VAR-");
        assertThat(result.getScopeType()).isEqualTo("ACCOUNT");
        assertThat(result.getSampleCount()).isEqualTo(120);
        assertThat(result.getVarValue()).isPositive();
        ArgumentCaptor<VarCalculation> captor = ArgumentCaptor.forClass(VarCalculation.class);
        verify(varMapper).insert(captor.capture());
        assertThat(captor.getValue().getScopeId()).isEqualTo("ACC-1");
        assertThat(captor.getValue().getPnlHistJson()).contains("sampleCount");
    }

    @Test
    @DisplayName("行情样本不足时抛出 INSUFFICIENT_DATA 且不落库")
    void insufficientDataPropagates() {
        when(positionMapper.selectByAccount("ACC-1"))
                .thenReturn(List.of(position("rb", "LONG", "10")));
        when(marketDataSource.closingPrices(anyString(), anyInt()))
                .thenReturn(series(10));

        assertThatThrownBy(() -> service.compute(request("ACCOUNT", "ACC-1"),
                UserContext.of("risk-1", "RISK_OFFICER", null)))
                .isInstanceOf(TradeRejectedException.class)
                .hasMessageContaining("INSUFFICIENT_DATA");
        verify(varMapper, never()).insert(any());
    }

    @Test
    @DisplayName("组合 VaR 超过风险预算时标记 budgetBreached")
    void portfolioBudgetBreached() {
        Portfolio portfolio = new Portfolio();
        portfolio.setPortfolioId("PF-1");
        portfolio.setOwner("trader-1");
        portfolio.setConfigJson("{\"risk_budget\":{\"max_var_95_1d\":1}}");
        when(portfolioMapper.selectByPortfolioId("PF-1")).thenReturn(portfolio);
        when(portfolioPositionMapper.selectByPortfolioId("PF-1"))
                .thenReturn(List.of(portfolioPosition("rb", "LONG", "10")));
        when(marketDataSource.closingPrices(anyString(), anyInt()))
                .thenReturn(alternatingSeries());

        VarResult result = service.compute(request("PORTFOLIO", "PF-1"),
                UserContext.of("admin", "ADMIN", null));

        assertThat(result.getRiskBudgetLimit()).isEqualByComparingTo("1");
        assertThat(result.isBudgetBreached()).isTrue();
    }

    @Test
    @DisplayName("TRADER 访问他人组合同样被拒绝")
    void portfolioAccessDeniedForOtherTrader() {
        Portfolio portfolio = new Portfolio();
        portfolio.setPortfolioId("PF-1");
        portfolio.setOwner("trader-1");
        when(portfolioMapper.selectByPortfolioId("PF-1")).thenReturn(portfolio);

        assertThatThrownBy(() -> service.compute(request("PORTFOLIO", "PF-1"),
                UserContext.of("trader-2", "TRADER", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("无权");
        verify(varMapper, never()).insert(any());
    }

    @Test
    @DisplayName("最近一次 VaR 直接委托 Mapper")
    void latestDelegatesToMapper() {
        VarCalculation record = new VarCalculation();
        record.setCalcId("VAR-X");
        when(varMapper.selectLatest("PORTFOLIO", "PF-1")).thenReturn(record);

        VarCalculation latest = service.latest("portfolio", "PF-1",
                UserContext.of("admin", "ADMIN", null));

        assertThat(latest).isSameAs(record);
    }

    @Test
    @DisplayName("历史查询未传时间时使用默认回溯窗口")
    void historyAppliesDefaultWindow() {
        when(varMapper.selectHistory(eq("ACCOUNT"), eq("ACC-1"), any(), any()))
                .thenReturn(List.of());
        service.history("ACCOUNT", "ACC-1", null, null,
                UserContext.of("admin", "ADMIN", null));

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(varMapper).selectHistory(eq("ACCOUNT"), eq("ACC-1"),
                fromCaptor.capture(), any());
        assertThat(fromCaptor.getValue()).isBefore(LocalDateTime.now().minusDays(179));
    }

    private static VarComputeRequest request(String scopeType, String scopeId) {
        VarComputeRequest request = new VarComputeRequest();
        request.setScopeType(scopeType);
        request.setScopeId(scopeId);
        request.setConfidence(new BigDecimal("0.95"));
        request.setHorizonDays(1);
        return request;
    }

    private static Position position(String symbol, String direction, String quantity) {
        Position position = new Position();
        position.setSymbol(symbol);
        position.setDirection(direction);
        position.setQuantity(new BigDecimal(quantity));
        return position;
    }

    private static PortfolioPosition portfolioPosition(String symbol, String direction,
                                                       String quantity) {
        PortfolioPosition position = new PortfolioPosition();
        position.setSymbol(symbol);
        position.setDirection(direction);
        position.setCurQty(new BigDecimal(quantity));
        return position;
    }

    private static List<BigDecimal> alternatingSeries() {
        List<BigDecimal> values = new ArrayList<>(121);
        double price = 3500d;
        for (int i = 0; i < 121; i++) {
            values.add(BigDecimal.valueOf(price).setScale(4, RoundingMode.HALF_UP));
            price *= (i % 2 == 0) ? 1.02 : 0.985;
        }
        return values;
    }

    private static List<BigDecimal> series(int size) {
        List<BigDecimal> values = new ArrayList<>(size);
        double price = 3500d;
        for (int i = 0; i < size; i++) {
            values.add(BigDecimal.valueOf(price).setScale(4, RoundingMode.HALF_UP));
            price *= 1.001;
        }
        return values;
    }
}
