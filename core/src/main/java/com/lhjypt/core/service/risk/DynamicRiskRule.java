package com.lhjypt.core.service.risk;

/**
 * 支持运行时阈值注入的事前风控规则。
 * 参数格式为 JSON 对象；实现方负责校验并只接受自己声明的键。
 */
public interface DynamicRiskRule extends RiskRule {

    /**
     * 默认阈值（JSON），用于前端展示与恢复默认。
     */
    String getDefaultThresholds();

    /**
     * 应用阈值；非法参数抛出 IllegalArgumentException，且不得留下部分修改。
     */
    void applyThresholds(String thresholdParams);
}
