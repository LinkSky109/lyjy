package com.lhjypt.core.service.portfolio;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lhjypt.core.common.exception.TradeRejectedException;
import com.lhjypt.core.mapper.PortfolioMapper;
import com.lhjypt.core.mapper.PortfolioPositionMapper;
import com.lhjypt.core.mapper.PortfolioRebalanceJobMapper;
import com.lhjypt.core.model.portfolio.Portfolio;
import com.lhjypt.core.model.portfolio.PortfolioPosition;
import com.lhjypt.core.model.portfolio.PortfolioRebalanceJob;
import com.lhjypt.core.model.portfolio.RebalanceInstruction;
import com.lhjypt.core.model.portfolio.RebalancePreview;
import com.lhjypt.core.model.trade.TradeRequest;
import com.lhjypt.core.service.trade.TradeApplicationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 组合定义、目标持仓和再平衡执行。
 */
@Service
@RequiredArgsConstructor
public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);

    private final PortfolioMapper portfolioMapper;
    private final PortfolioPositionMapper positionMapper;
    private final PortfolioRebalanceJobMapper jobMapper;
    private final RebalanceEngine rebalanceEngine;
    private final TradeApplicationService tradeApplicationService;

    @Transactional
    public Portfolio create(Portfolio portfolio, String owner) {
        if (portfolio == null || portfolio.getName() == null
                || portfolio.getName().isBlank()) {
            throw new IllegalArgumentException("组合名称不能为空");
        }
        portfolio.setPortfolioId(
                portfolio.getPortfolioId() == null || portfolio.getPortfolioId().isBlank()
                        ? "PF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
                        : portfolio.getPortfolioId());
        portfolio.setOwner(owner);
        portfolio.setStatus("ACTIVE");
        portfolio.setBaseCurrency(
                portfolio.getBaseCurrency() == null ? "CNY" : portfolio.getBaseCurrency());
        portfolio.setConfigJson(
                portfolio.getConfigJson() == null ? "{}" : portfolio.getConfigJson());
        portfolio.setCreateTime(LocalDateTime.now());
        portfolio.setUpdateTime(LocalDateTime.now());
        portfolioMapper.insert(portfolio);
        return portfolio;
    }

    public List<Portfolio> list(String owner, boolean admin) {
        return admin ? portfolioMapper.selectList(null) : portfolioMapper.selectByOwner(owner);
    }

    @Transactional
    public List<PortfolioPosition> setPositions(String portfolioId, String owner,
                                                List<PortfolioPosition> positions) {
        Portfolio portfolio = requireOwned(portfolioId, owner);
        List<PortfolioPosition> old = positionMapper.selectByPortfolioId(portfolioId);
        positionMapper.deleteByPortfolioId(portfolioId);
        for (PortfolioPosition position : positions) {
            if (position.getSymbol() == null || position.getSymbol().isBlank()) {
                throw new IllegalArgumentException("组合持仓 symbol 不能为空");
            }
            if (position.getTargetQty() == null) {
                position.setTargetQty(BigDecimal.ZERO);
            }
            position.setPortfolioId(portfolioId);
            position.setDirection(position.getDirection() == null
                    ? "LONG" : position.getDirection().toUpperCase());
            PortfolioPosition existing = old.stream()
                    .filter(item -> item.getSymbol().equals(position.getSymbol())
                            && item.getDirection().equals(position.getDirection()))
                    .findFirst().orElse(null);
            position.setCurQty(existing == null ? BigDecimal.ZERO : existing.getCurQty());
            position.setUpdateTime(LocalDateTime.now());
            positionMapper.insert(position);
        }
        portfolio.setUpdateTime(LocalDateTime.now());
        portfolioMapper.updateById(portfolio);
        return positionMapper.selectByPortfolioId(portfolioId);
    }

    public Map<String, Object> detail(String portfolioId, String requester, boolean admin) {
        Portfolio portfolio = requireReadable(portfolioId, requester, admin);
        List<PortfolioPosition> positions = positionMapper.selectByPortfolioId(portfolioId);
        RebalancePreview preview = rebalanceEngine.build(portfolio, positions);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("portfolio", portfolio);
        result.put("positions", positions);
        result.put("preview", preview);
        result.put("deviations", positions.stream().map(position -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("symbol", position.getSymbol());
            item.put("direction", position.getDirection());
            item.put("deviation", safe(position.getTargetQty())
                    .subtract(safe(position.getCurQty())));
            return item;
        }).toList());
        return result;
    }

    public synchronized PortfolioRebalanceJob trigger(String portfolioId,
                                                       String triggerType,
                                                       String executeMode,
                                                       String owner) {
        Portfolio portfolio = requireOwned(portfolioId, owner);
        if (jobMapper.selectActiveByPortfolio(portfolioId) != null) {
            throw new IllegalStateException("该组合已有进行中的再平衡任务");
        }
        PortfolioRebalanceJob job = new PortfolioRebalanceJob();
        job.setJobId("RB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        job.setPortfolioId(portfolioId);
        job.setTriggerType(triggerType == null ? "MANUAL" : triggerType.toUpperCase());
        job.setExecuteMode(executeMode == null ? "MANUAL" : executeMode.toUpperCase());
        job.setStatus(PortfolioRebalanceJob.PENDING);
        job.setCreateTime(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());
        jobMapper.insert(job);

        RebalancePreview preview = rebalanceEngine.build(
                portfolio, positionMapper.selectByPortfolioId(portfolioId));
        job.setPreviewJson(JSON.toJSONString(preview));
        job.setDiffTotal(preview.getDiffTotal());
        job.setCostEst(preview.getCostEst());
        job.setStatus(PortfolioRebalanceJob.PREVIEW_READY);
        job.setUpdateTime(LocalDateTime.now());
        jobMapper.updateById(job);
        return job;
    }

    public RebalancePreview preview(String jobId, String requester, boolean admin) {
        PortfolioRebalanceJob job = requireJob(jobId);
        requireReadable(job.getPortfolioId(), requester, admin);
        return parsePreview(job.getPreviewJson());
    }

    public PortfolioRebalanceJob confirm(String jobId, String requester, boolean admin) {
        PortfolioRebalanceJob job = requireJob(jobId);
        Portfolio portfolio = requireReadable(job.getPortfolioId(), requester, admin);
        if (PortfolioRebalanceJob.COMPLETED.equals(job.getStatus())
                || PortfolioRebalanceJob.PARTIALLY_COMPLETED.equals(job.getStatus())) {
            return job;
        }
        if (!PortfolioRebalanceJob.PREVIEW_READY.equals(job.getStatus())) {
            throw new IllegalStateException("再平衡任务当前状态不可执行: " + job.getStatus());
        }

        job.setStatus(PortfolioRebalanceJob.RUNNING);
        job.setUpdateTime(LocalDateTime.now());
        jobMapper.updateById(job);

        RebalancePreview preview = parsePreview(job.getPreviewJson());
        String accountId = parseAccountId(portfolio.getConfigJson());
        int success = 0;
        int attempted = 0;
        for (RebalanceInstruction instruction : preview.getInstructions()) {
            if (!RebalanceInstruction.PENDING.equals(instruction.getStatus())) {
                continue;
            }
            attempted++;
            try {
                TradeRequest request = toTradeRequest(
                        portfolio, instruction, accountId, job.getJobId());
                var result = tradeApplicationService.submit(request);
                instruction.setStatus(RebalanceInstruction.SUCCESS);
                instruction.setOrderId(result.orderId());
                updateCurrentQuantity(portfolio.getPortfolioId(), instruction);
                success++;
            } catch (Exception error) {
                instruction.setStatus(RebalanceInstruction.FAILED);
                instruction.setReason(error instanceof TradeRejectedException rejected
                        ? rejected.getCode() + ": " + rejected.getMessage()
                        : error.getMessage());
                log.warn("组合调仓指令失败: job={} symbol={} reason={}",
                        jobId, instruction.getSymbol(), instruction.getReason());
            }
        }
        job.setPreviewJson(JSON.toJSONString(preview));
        job.setStatus(attempted == 0 ? PortfolioRebalanceJob.FAILED
                : success == attempted ? PortfolioRebalanceJob.COMPLETED
                : success > 0 ? PortfolioRebalanceJob.PARTIALLY_COMPLETED
                : PortfolioRebalanceJob.FAILED);
        job.setExecutedAt(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());
        jobMapper.updateById(job);
        return job;
    }

    private void updateCurrentQuantity(String portfolioId,
                                       RebalanceInstruction instruction) {
        boolean close = !"OPEN".equalsIgnoreCase(instruction.getOffsetFlag());
        String side;
        if (close) {
            side = "SELL".equalsIgnoreCase(instruction.getDirection()) ? "LONG" : "SHORT";
        } else {
            side = "BUY".equalsIgnoreCase(instruction.getDirection()) ? "LONG" : "SHORT";
        }
        PortfolioPosition position = positionMapper.selectUnique(
                portfolioId, instruction.getSymbol(), side);
        if (position != null) {
            position.setCurQty(position.getTargetQty());
            position.setUpdateTime(LocalDateTime.now());
            positionMapper.updateById(position);
        }
    }

    private TradeRequest toTradeRequest(Portfolio portfolio,
                                        RebalanceInstruction instruction,
                                        String accountId,
                                        String jobId) {
        TradeRequest request = new TradeRequest();
        request.setAccountId(accountId);
        request.setStrategyId(portfolio.getPortfolioId());
        request.setSymbol(instruction.getSymbol());
        request.setExchange(instruction.getExchange());
        request.setDirection(instruction.getDirection());
        request.setOffsetFlag(instruction.getOffsetFlag());
        request.setOrderType("LIMIT");
        request.setPrice(instruction.getPrice());
        request.setQuantity(instruction.getQuantity());
        request.setIdempotencyKey(jobId + "-" + instruction.getIndex());
        request.setRemark("组合再平衡 " + portfolio.getPortfolioId());
        return request;
    }

    private static String parseAccountId(String configJson) {
        try {
            JSONObject config = JSON.parseObject(
                    configJson == null || configJson.isBlank() ? "{}" : configJson);
            String accountId = config.getString("accountId");
            return accountId == null || accountId.isBlank() ? "DEFAULT-001" : accountId;
        } catch (Exception error) {
            return "DEFAULT-001";
        }
    }

    private static RebalancePreview parsePreview(String json) {
        if (json == null || json.isBlank()) {
            return new RebalancePreview();
        }
        RebalancePreview preview = JSON.parseObject(json, RebalancePreview.class);
        return preview == null ? new RebalancePreview() : preview;
    }

    private Portfolio requireOwned(String portfolioId, String owner) {
        Portfolio portfolio = portfolioMapper.selectByPortfolioId(portfolioId);
        if (portfolio == null) {
            throw new IllegalArgumentException("组合不存在: " + portfolioId);
        }
        if (!portfolio.getOwner().equals(owner)) {
            throw new IllegalStateException("无权操作该组合");
        }
        return portfolio;
    }

    private Portfolio requireReadable(String portfolioId, String requester, boolean admin) {
        Portfolio portfolio = portfolioMapper.selectByPortfolioId(portfolioId);
        if (portfolio == null) {
            throw new IllegalArgumentException("组合不存在: " + portfolioId);
        }
        if (!admin && !portfolio.getOwner().equals(requester)) {
            throw new IllegalStateException("无权查看该组合");
        }
        return portfolio;
    }

    private PortfolioRebalanceJob requireJob(String jobId) {
        PortfolioRebalanceJob job = jobMapper.selectByJobId(jobId);
        if (job == null) {
            throw new IllegalArgumentException("再平衡任务不存在: " + jobId);
        }
        return job;
    }

    private static BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
