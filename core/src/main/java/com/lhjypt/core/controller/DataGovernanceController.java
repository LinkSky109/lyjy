package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.model.data.AdjustmentFactor;
import com.lhjypt.core.model.data.DataQualityStats;
import com.lhjypt.core.model.data.UnifiedMarketData;
import com.lhjypt.core.model.market.TickData;
import com.lhjypt.core.service.data.AdjustmentService;
import com.lhjypt.core.service.data.DataQualityMonitor;
import com.lhjypt.core.service.data.UnifiedDataModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 数据治理API - 清洗、复权、质量监控
 */
@RestController
@RequestMapping("/api/data-governance")
@RequiredArgsConstructor
public class DataGovernanceController {

    private final UnifiedDataModelService unifiedDataModelService;
    private final AdjustmentService adjustmentService;
    private final DataQualityMonitor dataQualityMonitor;

    /**
     * 数据清洗测试 - 提交一条tick数据进行清洗
     */
    @PostMapping("/clean")
    public Result<UnifiedMarketData> clean(@RequestBody TickData tick) {
        UnifiedMarketData result = unifiedDataModelService.process(tick, "API_TEST");
        return Result.success(result);
    }

    /**
     * 注册复权因子
     */
    @PostMapping("/adjustment/factors")
    public Result<Void> registerFactors(@RequestParam String symbol,
                                        @RequestBody List<AdjustmentFactor> factors) {
        adjustmentService.registerFactor(symbol, factors);
        return Result.success();
    }

    /**
     * 计算除权参考价
     */
    @PostMapping("/adjustment/ex-reference-price")
    public Result<BigDecimal> calcExReferencePrice(@RequestParam BigDecimal preClose,
                                                   @RequestBody AdjustmentFactor event) {
        return Result.success(adjustmentService.calcExReferencePrice(preClose, event));
    }

    /**
     * 前复权计算
     */
    @GetMapping("/adjustment/forward")
    public Result<BigDecimal> forwardAdjust(@RequestParam BigDecimal price,
                                            @RequestParam String symbol,
                                            @RequestParam String date) {
        return Result.success(adjustmentService.forwardAdjust(price, symbol, LocalDate.parse(date)));
    }

    /**
     * 后复权计算
     */
    @GetMapping("/adjustment/backward")
    public Result<BigDecimal> backwardAdjust(@RequestParam BigDecimal price,
                                             @RequestParam String symbol,
                                             @RequestParam String date) {
        return Result.success(adjustmentService.backwardAdjust(price, symbol, LocalDate.parse(date)));
    }

    /**
     * 获取指定标的数据质量统计
     */
    @GetMapping("/quality/{symbol}")
    public Result<DataQualityStats> getQualityStats(@PathVariable String symbol) {
        return Result.success(dataQualityMonitor.getStats(symbol));
    }

    /**
     * 获取所有标的数据质量统计
     */
    @GetMapping("/quality/all")
    public Result<Map<String, DataQualityStats>> getAllQualityStats() {
        return Result.success(dataQualityMonitor.getAllStats());
    }

    /**
     * 检查标的行情是否延迟
     */
    @GetMapping("/quality/delayed/{symbol}")
    public Result<Boolean> isDelayed(@PathVariable String symbol) {
        return Result.success(dataQualityMonitor.isDelayed(symbol));
    }

    /**
     * 重置数据质量统计
     */
    @PostMapping("/quality/reset")
    public Result<Void> resetStats(@RequestParam(required = false) String symbol) {
        if (symbol != null) {
            dataQualityMonitor.reset(symbol);
        } else {
            dataQualityMonitor.resetAll();
        }
        return Result.success();
    }
}
