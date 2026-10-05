package com.lhjypt.core.model.strategy;

import lombok.Data;

/**
 * 策略源码上传请求体。
 */
@Data
public class StrategyCodeRequest {

    private String name;

    private String sourceCode;
}
