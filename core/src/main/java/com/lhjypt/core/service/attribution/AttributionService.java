package com.lhjypt.core.service.attribution;

import com.alibaba.fastjson2.JSON;
import com.lhjypt.core.mapper.AttributionReportMapper;
import com.lhjypt.core.model.attribution.AttributionReport;
import com.lhjypt.core.model.attribution.BrinsonLine;
import com.lhjypt.core.model.attribution.BrinsonRequest;
import com.lhjypt.core.model.attribution.ExecutionRequest;
import com.lhjypt.core.model.attribution.FactorRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 绩效归因服务（P2 模块二 §2.5）。
 *
 * <ul>
 *   <li>Brinson：分配效应 = (wP-wB)×(rB_g-rB)、选股效应 = wB×(rP_g-rB_g)、交互效应 = (wP-wB)×(rP_g-rB_g)；</li>
 *   <li>因子暴露：Σ 权重×暴露度 按因子聚合；</li>
 *   <li>执行归因：IS 与滑点（bps）+ 机会成本占位；</li>
 * </ul>
 * 结果按类型/作用域/周期落 {@code attribution_report}，可追溯审计。
 */
@Service
@RequiredArgsConstructor
public class AttributionService {

    private static final double EPSILON = 1e-9;

    private final AttributionReportMapper reportMapper;

    public Map<String, Object> brinson(BrinsonRequest request) {
        require(request.getLines() != null && !request.getLines().isEmpty(), "lines 不能为空");
        double portfolioReturn = 0d;
        double benchmarkReturn = 0d;
        for (BrinsonLine line : request.getLines()) {
            portfolioReturn += w(line.getPortfolioWeight()) * r(line.getPortfolioReturn());
            benchmarkReturn += w(line.getBenchmarkWeight()) * r(line.getBenchmarkReturn());
        }

        List<Map<String, Object>> groups = new ArrayList<>();
        double alloc = 0d;
        double selection = 0d;
        double interaction = 0d;
        for (BrinsonLine line : request.getLines()) {
            double wP = w(line.getPortfolioWeight());
            double wB = w(line.getBenchmarkWeight());
            double rP = r(line.getPortfolioReturn());
            double rB = r(line.getBenchmarkReturn());
            double allocationEffect = (wP - wB) * (rB - benchmarkReturn);
            double selectionEffect = wB * (rP - rB);
            double interactionEffect = (wP - wB) * (rP - rB);
            alloc += allocationEffect;
            selection += selectionEffect;
            interaction += interactionEffect;

            Map<String, Object> group = new LinkedHashMap<>();
            group.put("group", line.getGroup());
            group.put("allocationEffect", allocationEffect);
            group.put("selectionEffect", selectionEffect);
            group.put("interactionEffect", interactionEffect);
            group.put("totalEffect", allocationEffect + selectionEffect + interactionEffect);
            groups.add(group);
        }

        double excessReturn = portfolioReturn - benchmarkReturn;
        double totalEffect = alloc + selection + interaction;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", AttributionReport.TYPE_BRINSON);
        result.put("portfolioReturn", portfolioReturn);
        result.put("benchmarkReturn", benchmarkReturn);
        result.put("excessReturn", excessReturn);
        result.put("allocationEffect", alloc);
        result.put("selectionEffect", selection);
        result.put("interactionEffect", interaction);
        result.put("totalEffect", totalEffect);
        result.put("identityCheck", Math.abs(excessReturn - totalEffect) < EPSILON);
        result.put("groups", groups);
        return result;
    }

    public Map<String, Object> factor(FactorRequest request) {
        require(request.getWeights() != null && !request.getWeights().isEmpty(), "weights 不能为空");
        require(request.getExposures() != null, "exposures 不能为空");

        Set<String> factors = new LinkedHashSet<>();
        request.getExposures().values().forEach(item -> factors.addAll(item.keySet()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", AttributionReport.TYPE_FACTOR);
        Map<String, BigDecimal> exposures = new LinkedHashMap<>();
        for (String factor : factors) {
            double sum = 0d;
            for (Map.Entry<String, BigDecimal> entry : request.getWeights().entrySet()) {
                BigDecimal exposure = request.getExposures()
                        .getOrDefault(entry.getKey(), Map.of())
                        .getOrDefault(factor, BigDecimal.ZERO);
                sum += entry.getValue().doubleValue() * exposure.doubleValue();
            }
            exposures.put(factor, BigDecimal.valueOf(sum));
        }
        double totalWeight = request.getWeights().values().stream()
                .mapToDouble(BigDecimal::doubleValue).sum();
        result.put("factorExposures", exposures);
        result.put("totalWeight", totalWeight);
        return result;
    }

    public Map<String, Object> execution(ExecutionRequest request) {
        require(request.getSide() != null && request.getDecisionPrice() != null
                        && request.getArrivalPrice() != null, "side/decisionPrice/arrivalPrice 不能为空");
        boolean buy = "BUY".equalsIgnoreCase(request.getSide().trim());
        double decision = request.getDecisionPrice().doubleValue();
        double arrival = request.getArrivalPrice().doubleValue();
        double fill = request.getFillPrice() == null ? decision : request.getFillPrice().doubleValue();
        double quantity = request.getFillQuantity() == null ? 0d : request.getFillQuantity().doubleValue();
        double notional = fill * quantity;

        double sign = buy ? 1d : -1d;
        double isBps = decision != 0d ? sign * (fill - decision) / decision * 1e4 : 0d;
        double slippageBps = arrival != 0d ? sign * (fill - arrival) / arrival * 1e4 : 0d;
        double commission = request.getCommission() == null ? 0d : request.getCommission().doubleValue();
        double commissionBps = notional != 0d ? commission / notional * 1e4 : 0d;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", AttributionReport.TYPE_EXECUTION);
        result.put("side", request.getSide().trim().toUpperCase());
        result.put("notional", notional);
        result.put("implementationShortfallBps", isBps);
        result.put("slippageBps", slippageBps);
        result.put("commissionBps", commissionBps);
        result.put("opportunityCostBps", 0d); // 全量成交场景机会成本为 0，未成交部分由收盘价补齐
        if (request.getClosePrice() != null) {
            double close = request.getClosePrice().doubleValue();
            result.put("closePrice", close);
        }
        return result;
    }

    @Transactional
    public AttributionReport save(AttributionReport report, String operator) {
        report.setReportId("ATT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        report.setOperator(operator);
        report.setCreateTime(java.time.LocalDateTime.now());
        reportMapper.insert(report);
        return report;
    }

    public List<AttributionReport> reportList() {
        return reportMapper.selectAll();
    }

    public AttributionReport reportDetail(String reportId) {
        AttributionReport report = reportMapper.selectByReportId(reportId);
        if (report == null) {
            throw new IllegalArgumentException("归因报告不存在: " + reportId);
        }
        return report;
    }

    public static Map<String, Object> meta(String scopeType, String scopeId,
                                           LocalDate start, LocalDate end) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("scopeType", scopeType);
        meta.put("scopeId", scopeId);
        meta.put("periodStart", start);
        meta.put("periodEnd", end);
        return meta;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static double w(BigDecimal value) {
        return th(value);
    }

    private static double r(BigDecimal value) {
        return th(value);
    }

    private static double th(BigDecimal value) {
        return value == null ? 0d : value.doubleValue();
    }
}