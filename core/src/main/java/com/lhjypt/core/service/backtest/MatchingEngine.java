package com.lhjypt.core.service.backtest;

import com.lhjypt.core.model.backtest.BacktestConfig;
import com.lhjypt.core.model.backtest.TradeRecord;
import com.lhjypt.core.model.market.TickData;

import java.math.BigDecimal;
import java.util.List;

/**
 * 撮合引擎接口
 * 模拟交易所撮合规则，将订单与行情进行匹配
 */
public interface MatchingEngine {

    /**
     * 初始化撮合引擎
     */
    void init(BacktestConfig config);

    /**
     * 提交限价买单
     * @return 订单ID
     */
    String submitBuyLimit(String symbol, BigDecimal price, BigDecimal quantity);

    /**
     * 提交限价卖单
     */
    String submitSellLimit(String symbol, BigDecimal price, BigDecimal quantity);

    /**
     * 提交市价买单（以当前tick的卖一价成交）
     */
    String submitBuyMarket(String symbol, BigDecimal quantity);

    /**
     * 提交市价卖单（以当前tick的买一价成交）
     */
    String submitSellMarket(String symbol, BigDecimal quantity);

    /**
     * 撤销订单
     */
    boolean cancelOrder(String orderId);

    /**
     * 处理一个tick，撮合所有挂单
     * @return 本tick产生的成交记录
     */
    List<TradeRecord> processTick(TickData tick);

    /**
     * 获取当前挂单数量
     */
    int getPendingOrderCount();

    /**
     * 重置撮合引擎
     */
    void reset();
}
