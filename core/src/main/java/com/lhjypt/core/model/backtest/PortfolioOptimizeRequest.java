package com.lhjypt.core.model.backtest;

import lombok.Data;

import java.util.List;

/** 组合优化请求（OBS-014）：多策略资金分配。 */
@Data
public class PortfolioOptimizeRequest {

    public static final String METHOD_EQUAL = "EQUAL";
    public static final String METHOD_RISK_PARITY = "RISK_PARITY";
    public static final String METHOD_MAX_SHARPE = "MAX_SHARPE";

    /** 组合成员策略（各自 参数化双均线） */
    private List<Member> strategies;

    private String frequency = "1h";
    /** close 信号模式（组合权重基于每 bar 收益，建议 close） */
    private String mode = "close";
    private String startTime;
    private String endTime;
    private String method = METHOD_EQUAL;
    /** MAX_SHARPE 网格步长（0.05~0.5） */
    private Double step = 0.1;

    @Data
    public static class Member {
        private String symbol = "BTC_USDT";
        private Integer fast = 5;
        private Integer slow = 20;
    }
}