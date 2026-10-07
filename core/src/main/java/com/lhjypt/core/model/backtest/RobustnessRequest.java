package com.lhjypt.core.model.backtest;

import lombok.Data;

import java.util.List;

/** 稳健性检验请求（OBS-015，防过拟合）。 */
@Data
public class RobustnessRequest {

    private String symbol = "BTC_USDT";
    /** 多标的（非空优先） */
    private List<String> symbols;
    private String frequency = "1h";
    private String mode = "close";
    private String startTime;
    private String endTime;
    /** 训练集占比（0.5~0.9） */
    private Double trainRatio = 0.7;
    /** 稳定性折数（连续子窗口） */
    private Integer folds = 4;
    private List<Integer> fastOptions = List.of(3, 5, 8, 10);
    private List<Integer> slowOptions = List.of(15, 20, 30, 40);
    private String objective = "TOTAL_RETURN";
    private Double maxDrawdownLimit = 0.5;
}