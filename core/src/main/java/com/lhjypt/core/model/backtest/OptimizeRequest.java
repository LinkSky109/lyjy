package com.lhjypt.core.model.backtest;

import lombok.Data;

import java.util.List;

/** 策略参数优化请求（OBS-011）。 */
@Data
public class OptimizeRequest {

    public static final String OBJ_SHARPE = "SHARPE";
    public static final String OBJ_RETURN = "TOTAL_RETURN";
    public static final String OBJ_CALMAR = "CALMAR";

    private String symbol = "BTC_USDT";
    private String frequency = "1h";
    private String startTime;
    private String endTime;
    /** ohlc / close 信号模式 */
    private String mode = "close";
    /** 快线周期候选 */
    private List<Integer> fastOptions = List.of(3, 5, 8);
    /** 慢线周期候选 */
    private List<Integer> slowOptions = List.of(15, 20, 30);
    /** 目标函数（upper） */
    private String objective = OBJ_SHARPE;
    /** 最大回撤上限（0-1，可选过滤） */
    private Double maxDrawdownLimit = 0.2;
    /** 单次成交数量（手/币） */
    private Double quantity = 1.0;
    /** 最大组合数（防爆） */
    private int maxRuns = 50;
}