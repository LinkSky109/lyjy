package com.lhjypt.core.model.backtest;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 稳健性检验结果（OBS-015）。 */
@Data
public class RobustnessResult {

    @Data
    public static class Window {
        private int index;
        private String fromTime;
        private String toTime;
        private OptimizeResult.Run best;
    }

    private String symbol;
    private String frequency;
    private String mode;
    private String objective;
    private int bars;
    private int trainBars;
    private int testBars;

    /** 样本内最优 */
    private OptimizeResult.Run trainBest;
    /** 样本外（用样本内最优参数）表现 */
    private OptimizeResult.Run oosBest;
    /** 退化率 = (oos - train) / max(|train|,1e-4) */
    private double degradation;

    /** 各折最优（稳定性） */
    private List<Window> folds = new ArrayList<>();
    /** 最优参数（fast/slow）在折间的命中率：最频繁参数命中数 / 折数 */
    private double paramStability;
    private String stableParam;

    /** ROBUST / OVERFIT_RISK / INSUFFICIENT_DATA */
    private String verdict;
}