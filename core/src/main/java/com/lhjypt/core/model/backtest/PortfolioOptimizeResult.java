package com.lhjypt.core.model.backtest;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 组合优化结果（OBS-014）。 */
@Data
public class PortfolioOptimizeResult {

    @Data
    public static class MemberResult {
        private String symbol;
        private int fast;
        private int slow;
        /** 该成员年化收益/波动/夏普 */
        private double annualReturn;
        private double annualVolatility;
        private double sharpe;
        /** 分配权重 */
        private double weight;
    }

    private String method;
    private String frequency;
    private String mode;
    private int bars;
    /** 组合年化收益/波动/夏普 */
    private double portfolioReturn;
    private double portfolioVolatility;
    private double portfolioSharpe;
    /** 等权对照夏普 */
    private double equalWeightSharpe;
    private List<MemberResult> members = new ArrayList<>();
    private String note;
}