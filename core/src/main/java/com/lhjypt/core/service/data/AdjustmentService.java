package com.lhjypt.core.service.data;

import com.lhjypt.core.model.data.AdjustmentFactor;
import com.lhjypt.core.model.data.UnifiedMarketData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 复权服务
 * 处理除权除息事件，支持前复权/后复权计算
 *
 * 复权公式：
 * - 除权后参考价 = (前收盘价 - 每股分红 + 配股价 × 配股比例) / (1 + 拆分比例 + 配股比例)
 * - 复权因子 = 前收盘价 / 除权后参考价
 * - 前复权价 = 原始价 × 累计因子（从最新往历史乘）
 * - 后复权价 = 原始价 / 累计因子（从历史往最新乘）
 */
@Service
@RequiredArgsConstructor
public class AdjustmentService {
    private static final Logger log = LoggerFactory.getLogger(AdjustmentService.class);


    /** 复权因子缓存：symbol -> 按日期排序的因子列表 */
    private final Map<String, List<AdjustmentFactor>> factorCache = new ConcurrentHashMap<>();

    /**
     * 计算除权除息后的参考价
     */
    public BigDecimal calcExReferencePrice(BigDecimal preClose, AdjustmentFactor event) {
        BigDecimal numerator = preClose;
        BigDecimal denominator = BigDecimal.ONE;

        // 减去每股分红
        if (event.getDividendPerShare() != null) {
            numerator = numerator.subtract(event.getDividendPerShare());
        }
        // 加上配股价值
        if (event.getRightsPrice() != null && event.getRightsRatio() != null) {
            numerator = numerator.add(event.getRightsPrice().multiply(event.getRightsRatio()));
            denominator = denominator.add(event.getRightsRatio());
        }
        // 拆分
        if (event.getSplitRatio() != null) {
            denominator = denominator.add(event.getSplitRatio());
        }

        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return preClose;
        }
        return numerator.divide(denominator, 8, RoundingMode.HALF_UP);
    }

    /**
     * 计算单次复权因子
     */
    public BigDecimal calcFactor(BigDecimal preClose, AdjustmentFactor event) {
        BigDecimal exRefPrice = calcExReferencePrice(preClose, event);
        if (exRefPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }
        return preClose.divide(exRefPrice, 8, RoundingMode.HALF_UP);
    }

    /**
     * 前复权 - 将历史价格调整为最新价格基准
     * @param originalPrice 原始价格
     * @param symbol 标的
     * @param date 该价格对应的日期
     * @return 前复权后的价格
     */
    public BigDecimal forwardAdjust(BigDecimal originalPrice, String symbol, LocalDate date) {
        List<AdjustmentFactor> factors = factorCache.get(symbol);
        if (factors == null || factors.isEmpty()) {
            return originalPrice;
        }

        // 累计该日期之后所有复权因子
        BigDecimal cumulative = BigDecimal.ONE;
        for (AdjustmentFactor f : factors) {
            if (f.getExDate().isAfter(date) || f.getExDate().isEqual(date)) {
                cumulative = cumulative.multiply(f.getCumulativeFactor() != null ? f.getCumulativeFactor() : f.getFactor());
            }
        }
        return originalPrice.multiply(cumulative).setScale(8, RoundingMode.HALF_UP);
    }

    /**
     * 后复权 - 将历史价格调整为上市首日基准
     */
    public BigDecimal backwardAdjust(BigDecimal originalPrice, String symbol, LocalDate date) {
        List<AdjustmentFactor> factors = factorCache.get(symbol);
        if (factors == null || factors.isEmpty()) {
            return originalPrice;
        }

        // 累计该日期之前所有复权因子
        BigDecimal cumulative = BigDecimal.ONE;
        for (AdjustmentFactor f : factors) {
            if (f.getExDate().isBefore(date)) {
                cumulative = cumulative.multiply(f.getCumulativeFactor() != null ? f.getCumulativeFactor() : f.getFactor());
            }
        }
        return originalPrice.divide(cumulative, 8, RoundingMode.HALF_UP);
    }

    /**
     * 对行情数据应用前复权
     */
    public UnifiedMarketData applyForwardAdjust(UnifiedMarketData data, LocalDate date) {
        if (data.isAdjusted()) return data;

        BigDecimal factor = getCumulativeFactor(data.getSymbol(), date, true);
        if (factor.compareTo(BigDecimal.ONE) == 0) {
            data.setAdjusted(true);
            return data;
        }

        data.setLastPrice(data.getLastPrice().multiply(factor));
        data.setPreClose(data.getPreClose().multiply(factor));
        data.setOpen(data.getOpen().multiply(factor));
        data.setHigh(data.getHigh().multiply(factor));
        data.setLow(data.getLow().multiply(factor));
        data.setAdjusted(true);
        return data;
    }

    /**
     * 注册复权因子
     */
    public void registerFactor(String symbol, List<AdjustmentFactor> factors) {
        // 按日期排序并计算累计因子
        factors.sort((a, b) -> a.getExDate().compareTo(b.getExDate()));
        BigDecimal cumulative = BigDecimal.ONE;
        for (AdjustmentFactor f : factors) {
            cumulative = cumulative.multiply(f.getFactor());
            f.setCumulativeFactor(cumulative);
        }
        factorCache.put(symbol, factors);
        log.info("注册复权因子: {} 共{}条", symbol, factors.size());
    }

    private BigDecimal getCumulativeFactor(String symbol, LocalDate date, boolean forward) {
        List<AdjustmentFactor> factors = factorCache.get(symbol);
        if (factors == null || factors.isEmpty()) return BigDecimal.ONE;

        BigDecimal cumulative = BigDecimal.ONE;
        for (AdjustmentFactor f : factors) {
            boolean apply = forward ?
                    (f.getExDate().isAfter(date) || f.getExDate().isEqual(date)) :
                    f.getExDate().isBefore(date);
            if (apply) {
                cumulative = cumulative.multiply(f.getFactor());
            }
        }
        return cumulative;
    }
}
