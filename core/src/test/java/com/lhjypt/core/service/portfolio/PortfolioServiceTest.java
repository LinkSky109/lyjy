package com.lhjypt.core.service.portfolio;

import com.lhjypt.core.common.exception.TradeRejectedException;
import com.lhjypt.core.mapper.PortfolioMapper;
import com.lhjypt.core.mapper.PortfolioPositionMapper;
import com.lhjypt.core.mapper.PortfolioRebalanceJobMapper;
import com.lhjypt.core.model.portfolio.Portfolio;
import com.lhjypt.core.model.portfolio.PortfolioPosition;
import com.lhjypt.core.model.portfolio.PortfolioRebalanceJob;
import com.lhjypt.core.model.portfolio.RebalanceInstruction;
import com.lhjypt.core.model.portfolio.RebalancePreview;
import com.lhjypt.core.model.trade.TradeSubmissionResult;
import com.lhjypt.core.service.trade.TradeApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioServiceTest {

    private PortfolioMapper portfolioMapper;
    private PortfolioPositionMapper positionMapper;
    private PortfolioRebalanceJobMapper jobMapper;
    private RebalanceEngine engine;
    private TradeApplicationService tradeService;
    private PortfolioService service;

    @BeforeEach
    void setUp() {
        portfolioMapper = mock(PortfolioMapper.class);
        positionMapper = mock(PortfolioPositionMapper.class);
        jobMapper = mock(PortfolioRebalanceJobMapper.class);
        engine = mock(RebalanceEngine.class);
        tradeService = mock(TradeApplicationService.class);
        service = new PortfolioService(
                portfolioMapper, positionMapper, jobMapper, engine, tradeService);
    }

    @Test
    void createsOwnedPortfolio() {
        Portfolio portfolio = new Portfolio();
        portfolio.setName("alpha");

        Portfolio created = service.create(portfolio, "trader-1");

        assertEquals("trader-1", created.getOwner());
        assertEquals("ACTIVE", created.getStatus());
        verify(portfolioMapper).insert(created);
    }

    @Test
    void preventsConcurrentActiveJobs() {
        Portfolio portfolio = portfolio();
        when(portfolioMapper.selectByPortfolioId("PF-1")).thenReturn(portfolio);
        when(jobMapper.selectActiveByPortfolio("PF-1"))
                .thenReturn(new PortfolioRebalanceJob());

        assertThrows(IllegalStateException.class,
                () -> service.trigger("PF-1", "MANUAL", "MANUAL", "trader-1"));
    }

    @Test
    void confirmsInstructionsAndIgnoresPartialFailure() {
        Portfolio portfolio = portfolio();
        PortfolioRebalanceJob job = new PortfolioRebalanceJob();
        job.setJobId("RB-1");
        job.setPortfolioId("PF-1");
        job.setStatus(PortfolioRebalanceJob.PREVIEW_READY);
        job.setPreviewJson(com.alibaba.fastjson2.JSON.toJSONString(
                previewWithTwoInstructions()));
        when(jobMapper.selectByJobId("RB-1")).thenReturn(job);
        when(portfolioMapper.selectByPortfolioId("PF-1")).thenReturn(portfolio);
        when(positionMapper.selectUnique(any(), any(), any()))
                .thenReturn(new PortfolioPosition());
        when(tradeService.submit(any()))
                .thenReturn(new TradeSubmissionResult(
                        "ORD-1", "FILLED", "000001", "mock-primary",
                        false, null, "MOCK", false))
                .thenThrow(new TradeRejectedException("INSUFFICIENT_FUNDS", "资金不足"));

        PortfolioRebalanceJob result = service.confirm("RB-1", "trader-1", false);

        assertEquals(PortfolioRebalanceJob.PARTIALLY_COMPLETED, result.getStatus());
        RebalancePreview stored = com.alibaba.fastjson2.JSON.parseObject(
                result.getPreviewJson(), RebalancePreview.class);
        assertEquals(RebalanceInstruction.SUCCESS, stored.getInstructions().get(0).getStatus());
        assertEquals(RebalanceInstruction.FAILED, stored.getInstructions().get(1).getStatus());
    }

    @Test
    void completedJobIsIdempotentOnRepeatConfirm() {
        Portfolio portfolio = portfolio();
        PortfolioRebalanceJob job = new PortfolioRebalanceJob();
        job.setJobId("RB-1");
        job.setPortfolioId("PF-1");
        job.setStatus(PortfolioRebalanceJob.COMPLETED);
        when(jobMapper.selectByJobId("RB-1")).thenReturn(job);
        when(portfolioMapper.selectByPortfolioId("PF-1")).thenReturn(portfolio);

        assertEquals(job, service.confirm("RB-1", "trader-1", false));
    }

    private static Portfolio portfolio() {
        Portfolio portfolio = new Portfolio();
        portfolio.setPortfolioId("PF-1");
        portfolio.setOwner("trader-1");
        portfolio.setConfigJson("{\"accountId\":\"DEFAULT-001\",\"priceMap\":{\"rb\":1}}");
        return portfolio;
    }

    private static RebalancePreview previewWithTwoInstructions() {
        RebalancePreview preview = new RebalancePreview();
        RebalanceInstruction first = instruction(0, "rb");
        RebalanceInstruction second = instruction(1, "cu");
        preview.setInstructions(List.of(first, second));
        preview.setCostEst(new BigDecimal("2"));
        preview.setDiffTotal(new BigDecimal("2"));
        return preview;
    }

    private static RebalanceInstruction instruction(int index, String symbol) {
        RebalanceInstruction instruction = new RebalanceInstruction();
        instruction.setIndex(index);
        instruction.setSymbol(symbol);
        instruction.setDirection("BUY");
        instruction.setOffsetFlag("OPEN");
        instruction.setQuantity(BigDecimal.ONE);
        instruction.setPrice(BigDecimal.ONE);
        instruction.setEstimatedCost(BigDecimal.ONE);
        instruction.setStatus(RebalanceInstruction.PENDING);
        return instruction;
    }
}
