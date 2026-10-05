package com.lhjypt.core.service.var;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lhjypt.core.common.auth.UserContext;
import com.lhjypt.core.mapper.PortfolioMapper;
import com.lhjypt.core.mapper.PortfolioPositionMapper;
import com.lhjypt.core.mapper.PositionMapper;
import com.lhjypt.core.mapper.VarCalculationMapper;
import com.lhjypt.core.model.Position;
import com.lhjypt.core.model.portfolio.Portfolio;
import com.lhjypt.core.model.portfolio.PortfolioPosition;
import com.lhjypt.core.model.var.VarCalculation;
import com.lhjypt.core.model.var.VarComputeRequest;
import com.lhjypt.core.model.var.VarPosition;
import com.lhjypt.core.model.var.VarResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * VaR 风险价值服务（PRD §5.7）。
 *
 * <p>组合/账户/策略持仓 + 历史日线 → 计算日损益序列 → 分位数/协方差 → 落库。
 * 联动：组合 {@code config_json.risk_budget.max_var_95_1d} 比对；
 * 最近值供事中风控 {@code VAR_LIMIT} 规则消费。
 */
@Service
@RequiredArgsConstructor
public class VarService {

    private static final Logger log = LoggerFactory.getLogger(VarService.class);

    /** 拉取的历史日线数量。 */
    private static final int LOOKBACK_DAYS = 260;

    public static final String SCOPE_PORTFOLIO = "PORTFOLIO";
    public static final String SCOPE_ACCOUNT = "ACCOUNT";
    public static final String SCOPE_STRATEGY = "STRATEGY";
    public static final String METHOD_HISTORICAL = "HISTORICAL";
    public static final String METHOD_VAR_COV = "VAR_COV";

    private final VarCalculationMapper varCalculationMapper;
    private final PortfolioMapper portfolioMapper;
    private final PortfolioPositionMapper portfolioPositionMapper;
    private final PositionMapper positionMapper;
    private final VarMarketDataSource marketDataSource;

    @Transactional
    public VarResult compute(VarComputeRequest request, UserContext context) {
        if (request == null) {
            throw new IllegalArgumentException("计算请求不能为空");
        }
        String scopeType = normalizeScope(request.getScopeType());
        String scopeId = requireText(request.getScopeId(), "scopeId 不能为空");
        String method = normalizeMethod(request.getMethod());
        BigDecimal confidence = request.getConfidence() == null
                ? new BigDecimal("0.95") : request.getConfidence();
        if (confidence.compareTo(BigDecimal.ZERO) <= 0
                || confidence.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("confidence 必须位于 (0,1) 区间");
        }
        int horizonDays = request.getHorizonDays() == null ? 1 : request.getHorizonDays();
        if (horizonDays < 1) {
            throw new IllegalArgumentException("horizonDays 必须 >= 1");
        }

        checkReadable(scopeType, scopeId, context);

        List<VarPosition> positions = loadPositions(scopeType, scopeId);
        Map<String, List<BigDecimal>> closes = new LinkedHashMap<>();
        for (String symbol : symbolsOf(positions)) {
            closes.put(symbol, marketDataSource.closingPrices(symbol, LOOKBACK_DAYS));
        }

        VarComputation computation = VarCalculator.compute(
                positions, closes, method, confidence.doubleValue(), horizonDays);

        BigDecimal budgetLimit = null;
        boolean breached = false;
        if (SCOPE_PORTFOLIO.equals(scopeType)) {
            budgetLimit = riskBudgetLimit(scopeId);
            if (budgetLimit != null
                    && BigDecimal.valueOf(computation.varValue()).compareTo(budgetLimit) > 0) {
                breached = true;
                log.warn("组合 {} VaR {} 突破风险预算 {}", scopeId,
                        computation.varValue(), budgetLimit);
            }
        }

        VarCalculation record = new VarCalculation();
        record.setCalcId("VAR-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
        record.setScopeType(scopeType);
        record.setScopeId(scopeId);
        record.setMethod(computation.method());
        record.setConfidence(confidence);
        record.setHorizonDays(horizonDays);
        record.setVarValue(toDecimal(computation.varValue()));
        record.setPnlHistJson(summaryJson(computation, budgetLimit, breached));
        record.setCalcTime(LocalDateTime.now());
        varCalculationMapper.insert(record);

        return toResult(record, computation, budgetLimit, breached);
    }

    public VarCalculation latest(String scopeType, String scopeId, UserContext context) {
        String normalizedScope = normalizeScope(scopeType);
        checkReadable(normalizedScope, requireText(scopeId, "scopeId 不能为空"), context);
        return varCalculationMapper.selectLatest(normalizedScope, scopeId);
    }

    /** 内部使用：不做权限校验的最近一次 VaR（供事中风控消费）。 */
    public VarCalculation latestRaw(String scopeType, String scopeId) {
        if (scopeType == null || scopeId == null) {
            return null;
        }
        return varCalculationMapper.selectLatest(scopeType.toUpperCase(), scopeId);
    }

    public List<VarCalculation> history(String scopeType, String scopeId,
                                        LocalDateTime from, LocalDateTime to,
                                        UserContext context) {
        String normalizedScope = normalizeScope(scopeType);
        checkReadable(normalizedScope, requireText(scopeId, "scopeId 不能为空"), context);
        LocalDateTime effectiveFrom = from == null ? LocalDateTime.now().minusDays(180) : from;
        LocalDateTime effectiveTo = to == null ? LocalDateTime.now() : to;
        return varCalculationMapper.selectHistory(
                normalizedScope, scopeId, effectiveFrom, effectiveTo);
    }

    private List<VarPosition> loadPositions(String scopeType, String scopeId) {
        List<VarPosition> positions = new ArrayList<>();
        switch (scopeType) {
            case SCOPE_PORTFOLIO -> {
                Portfolio portfolio = portfolioMapper.selectByPortfolioId(scopeId);
                if (portfolio == null) {
                    throw new IllegalArgumentException("组合不存在: " + scopeId);
                }
                for (PortfolioPosition position
                        : portfolioPositionMapper.selectByPortfolioId(scopeId)) {
                    positions.add(new VarPosition(position.getSymbol(),
                            position.getDirection(), position.getCurQty()));
                }
            }
            case SCOPE_ACCOUNT -> {
                for (Position position : positionMapper.selectByAccount(scopeId)) {
                    positions.add(new VarPosition(position.getSymbol(),
                            position.getDirection(), position.getQuantity()));
                }
            }
            case SCOPE_STRATEGY -> {
                for (Position position : positionMapper.selectByStrategy(scopeId)) {
                    positions.add(new VarPosition(position.getSymbol(),
                            position.getDirection(), position.getQuantity()));
                }
            }
            default -> throw new IllegalArgumentException("不支持的作用域类型: " + scopeType);
        }
        return positions;
    }

    private BigDecimal riskBudgetLimit(String portfolioId) {
        Portfolio portfolio = portfolioMapper.selectByPortfolioId(portfolioId);
        if (portfolio == null || portfolio.getConfigJson() == null) {
            return null;
        }
        try {
            JSONObject config = JSON.parseObject(portfolio.getConfigJson());
            JSONObject budget = config.getJSONObject("risk_budget");
            return budget == null ? null : budget.getBigDecimal("max_var_95_1d");
        } catch (Exception error) {
            log.warn("解析组合风险预算失败 portfolio={} err={}", portfolioId, error.getMessage());
            return null;
        }
    }

    private void checkReadable(String scopeType, String scopeId, UserContext context) {
        if (context == null || context.hasAnyRole(UserContext.ROLE_ADMIN,
                UserContext.ROLE_RISK_OFFICER, UserContext.ROLE_COMPLIANCE)) {
            return;
        }
        if (SCOPE_PORTFOLIO.equals(scopeType)) {
            Portfolio portfolio = portfolioMapper.selectByPortfolioId(scopeId);
            if (portfolio == null) {
                throw new IllegalArgumentException("组合不存在: " + scopeId);
            }
            if (!portfolio.getOwner().equals(context.userId())) {
                throw new IllegalStateException("无权访问该组合的 VaR");
            }
        }
    }

    private static String summaryJson(VarComputation computation,
                                      BigDecimal budgetLimit, boolean breached) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("method", computation.method());
        summary.put("confidence", computation.confidence());
        summary.put("horizonDays", computation.horizonDays());
        summary.put("sampleCount", computation.sampleCount());
        summary.put("varValue", toDecimal(computation.varValue()));
        summary.put("meanPnl", toDecimal(computation.meanPnl()));
        summary.put("stdPnl", toDecimal(computation.stdPnl()));
        summary.put("percentilePnl", toDecimal(computation.percentilePnl()));
        summary.put("minPnl", toDecimal(computation.minPnl()));
        summary.put("maxPnl", toDecimal(computation.maxPnl()));
        if (budgetLimit != null) {
            summary.put("riskBudgetLimit", budgetLimit);
        }
        summary.put("budgetBreached", breached);
        return JSON.toJSONString(summary);
    }

    private static VarResult toResult(VarCalculation record, VarComputation computation,
                                      BigDecimal budgetLimit, boolean breached) {
        VarResult result = new VarResult();
        result.setCalcId(record.getCalcId());
        result.setScopeType(record.getScopeType());
        result.setScopeId(record.getScopeId());
        result.setMethod(record.getMethod());
        result.setConfidence(record.getConfidence());
        result.setHorizonDays(record.getHorizonDays());
        result.setVarValue(record.getVarValue());
        result.setSampleCount(computation.sampleCount());
        result.setMeanPnl(toDecimal(computation.meanPnl()));
        result.setStdPnl(toDecimal(computation.stdPnl()));
        result.setPercentilePnl(toDecimal(computation.percentilePnl()));
        result.setMinPnl(toDecimal(computation.minPnl()));
        result.setMaxPnl(toDecimal(computation.maxPnl()));
        result.setRiskBudgetLimit(budgetLimit);
        result.setBudgetBreached(breached);
        result.setCalcTime(record.getCalcTime());
        return result;
    }

    private static Set<String> symbolsOf(List<VarPosition> positions) {
        Set<String> symbols = new LinkedHashSet<>();
        for (VarPosition position : positions) {
            if (position.symbol() != null && !position.symbol().isBlank()) {
                symbols.add(position.symbol());
            }
        }
        return symbols;
    }

    private static String normalizeScope(String scopeType) {
        String value = requireText(scopeType, "scopeType 不能为空").toUpperCase();
        return switch (value) {
            case SCOPE_PORTFOLIO, SCOPE_ACCOUNT, SCOPE_STRATEGY -> value;
            default -> throw new IllegalArgumentException(
                    "scopeType 必须是 PORTFOLIO/ACCOUNT/STRATEGY 之一: " + scopeType);
        };
    }

    private static String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return METHOD_HISTORICAL;
        }
        String value = method.toUpperCase();
        if (!METHOD_HISTORICAL.equals(value) && !METHOD_VAR_COV.equals(value)) {
            throw new IllegalArgumentException(
                    "method 必须是 HISTORICAL/VAR_COV 之一: " + method);
        }
        return value;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static BigDecimal toDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP);
    }
}
