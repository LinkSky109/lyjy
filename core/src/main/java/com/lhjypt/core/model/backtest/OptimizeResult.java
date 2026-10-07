package com.lhjypt.core.model.backtest;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 策略参数优化结果（OBS-011）。 */
@Data
public class OptimizeResult {

    @Data
    public static class Run {
        private int fast;
        private int slow;
        private double totalReturn;
        private double sharpe;
        private double maxDrawdown;
        private int trades;

        public Run() {
        }

        public Run(int fast, int slow, double totalReturn,
                   double sharpe, double maxDrawdown, int trades) {
            this.fast = fast;
            this.slow = slow;
            this.totalReturn = totalReturn;
            this.sharpe = sharpe;
            this.maxDrawdown = maxDrawdown;
            this.trades = trades;
        }
    }

    private String symbol;
    private String frequency;
    private String mode;
    private String objective;
    /** 批次（多标的一次寻优共享） */
    private String batchId;
    private int barsUsed;
    private Run best;
    /** 基准（默认 5/20）对照 */
    private Run baseline;
    /** 全部组合（按目标降序） */
    private List<Run> runs = new ArrayList<>();
    /** 被回撤上限过滤的组合数 */
    private int filteredByDrawdown;
    /** 该标的寻优失败原因（多标的容错） */
    private String error;
}