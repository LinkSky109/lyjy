package com.lhjypt.core.service.data;

import com.lhjypt.core.common.exception.DataQualityException;
import com.lhjypt.core.model.data.UnifiedMarketData;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 数据清洗服务
 * 检测并处理原始行情中的异常数据：
 * - 零值/负值价格
 * - 价格跳变（超过阈值）
 * - 缺失字段
 * - 时间异常（乱序、未来时间）
 * - 买卖盘倒挂（买一价 > 卖一价）
 */
@Service
@RequiredArgsConstructor
public class DataCleaningService {
    private static final Logger log = LoggerFactory.getLogger(DataCleaningService.class);


    @Value("${lhjypt.data-cleaning.price-jump-threshold:0.1}")
    private double priceJumpThreshold;

    @Value("${lhjypt.data-cleaning.enabled:true}")
    private boolean enabled;

    private final DataQualityMonitor qualityMonitor;

    /** 缓存上一个有效价格，用于跳变检测 */
    private final Map<String, BigDecimal> lastValidPrice = new ConcurrentHashMap<>();

    /**
     * 清洗单条行情数据
     * @return 清洗后的数据，异常数据标记qualityFlag
     */
    public UnifiedMarketData clean(UnifiedMarketData data) {
        if (!enabled) {
            data.setQualityFlag("CLEAN");
            return data;
        }

        qualityMonitor.recordReceived(data.getSymbol());

        try {
            // 1. 基础字段校验
            validateBasicFields(data);

            // 2. 价格合理性校验
            validatePrice(data);

            // 3. 价格跳变检测
            detectPriceJump(data);

            // 4. 买卖盘校验
            validateOrderBook(data);

            // 5. 时间校验
            validateTime(data);

            data.setQualityFlag("CLEAN");
            data.setQualityIssue(null);
            qualityMonitor.recordClean(data.getSymbol());
            lastValidPrice.put(data.getSymbol(), data.getLastPrice());

        } catch (DataQualityException e) {
            data.setQualityFlag("DIRTY");
            data.setQualityIssue(e.getErrorType() + ": " + e.getMessage());
            qualityMonitor.recordDirty(data.getSymbol(), e.getErrorType());
            log.warn("数据清洗异常: {} - {} - {}", data.getSymbol(), e.getErrorType(), e.getMessage());
        }

        return data;
    }

    /**
     * 判断数据是否可用（CLEAN或SUSPECT可用，DIRTY丢弃）
     */
    public boolean isUsable(UnifiedMarketData data) {
        return !"DIRTY".equals(data.getQualityFlag());
    }

    private void validateBasicFields(UnifiedMarketData data) {
        if (data.getSymbol() == null || data.getSymbol().isBlank()) {
            throw new DataQualityException("UNKNOWN", "MISSING_SYMBOL", "标的代码为空", data);
        }
        if (data.getLastPrice() == null) {
            qualityMonitor.recordMissing(data.getSymbol());
            throw new DataQualityException(data.getSymbol(), "MISSING_PRICE", "最新价为空", data);
        }
        if (data.getMarketTime() == null) {
            throw new DataQualityException(data.getSymbol(), "MISSING_TIME", "行情时间为空", data);
        }
    }

    private void validatePrice(UnifiedMarketData data) {
        BigDecimal price = data.getLastPrice();
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            qualityMonitor.recordZeroValue(data.getSymbol());
            throw new DataQualityException(data.getSymbol(), "ZERO_PRICE", "价格为零或负值: " + price, data);
        }
        // 价格上限保护（防止极端错价，单标的不超过1000万）
        if (price.compareTo(new BigDecimal("10000000")) > 0) {
            throw new DataQualityException(data.getSymbol(), "PRICE_TOO_HIGH", "价格异常偏高: " + price, data);
        }
    }

    private void detectPriceJump(UnifiedMarketData data) {
        BigDecimal lastPrice = lastValidPrice.get(data.getSymbol());
        if (lastPrice == null) return;

        BigDecimal change = data.getLastPrice().subtract(lastPrice)
                .divide(lastPrice, 6, BigDecimal.ROUND_HALF_UP)
                .abs();

        if (change.compareTo(BigDecimal.valueOf(priceJumpThreshold)) > 0) {
            qualityMonitor.recordPriceJump(data.getSymbol());
            // 价格跳变标记为可疑，但不丢弃（可能是真实涨跌停）
            data.setQualityFlag("SUSPECT");
            data.setQualityIssue("PRICE_JUMP: 价格跳变" + change.multiply(BigDecimal.valueOf(100)) + "%");
            log.info("价格跳变: {} {} -> {} ({}%)",
                    data.getSymbol(), lastPrice, data.getLastPrice(),
                    change.multiply(BigDecimal.valueOf(100)).setScale(2, BigDecimal.ROUND_HALF_UP));
        }
    }

    private void validateOrderBook(UnifiedMarketData data) {
        if (data.getBidPrices() == null || data.getAskPrices() == null) return;

        BigDecimal bid1 = data.getBidPrices().get(1);
        BigDecimal ask1 = data.getAskPrices().get(1);
        if (bid1 != null && ask1 != null && bid1.compareTo(ask1) > 0) {
            // 买卖盘倒挂，标记可疑
            data.setQualityFlag("SUSPECT");
            data.setQualityIssue("ORDER_BOOK_INVERTED: 买一价" + bid1 + " > 卖一价" + ask1);
        }
    }

    private void validateTime(UnifiedMarketData data) {
        if (data.getRecvTime() != null && data.getMarketTime() != null) {
            // 行情时间不应超过接收时间太多（允许5分钟时钟偏差）
            if (data.getMarketTime().isAfter(data.getRecvTime().plusMinutes(5))) {
                throw new DataQualityException(data.getSymbol(), "TIME_FUTURE",
                        "行情时间在未来: " + data.getMarketTime(), data);
            }
        }
    }
}
