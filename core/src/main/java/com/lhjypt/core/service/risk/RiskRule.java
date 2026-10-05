package com.lhjypt.core.service.risk;

import com.lhjypt.core.model.risk.RiskCheckResult;
import com.lhjypt.core.model.trade.TradeRequest;

/**
 * 风控规则接口
 * 所有事前风控规则实现此接口，可插拔扩展
 */
public interface RiskRule {

    /**
     * 规则编码
     */
    String getCode();

    /**
     * 规则名称
     */
    String getName();

    /**
     * 是否启用
     */
    boolean isEnabled();

    /**
     * 执行风控检查
     * @param request 下单请求
     * @param context 风控上下文（持仓、资金、频率统计等）
     */
    void check(TradeRequest request, RiskContext context, RiskCheckResult result);

    /**
     * 风控上下文 - 提供检查所需的运行时数据
     */
    interface RiskContext {
        /** 当前标的持仓数量 */
        java.math.BigDecimal getCurrentPosition(String symbol);

        /** 账户可用资金 */
        java.math.BigDecimal getAvailableCash();

        /** 账户总资金 */
        java.math.BigDecimal getTotalAsset();

        /** 策略在指定时间窗口内的下单次数 */
        int getOrderCount(String strategyId, int windowSeconds);

        /** 标的当前买一价 */
        java.math.BigDecimal getBidPrice(String symbol);

        /** 标的当前卖一价 */
        java.math.BigDecimal getAskPrice(String symbol);

        /** 是否在黑名单 */
        boolean isBlacklisted(String symbol);
    }
}
