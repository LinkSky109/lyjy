package com.lhjypt.core.common.exception;

/**
 * 数据质量异常 - 行情数据清洗时发现异常数据
 */
public class DataQualityException extends RuntimeException {

    private final String symbol;
    private final String errorType;
    private final Object rawData;

    public DataQualityException(String symbol, String errorType, String message, Object rawData) {
        super(message);
        this.symbol = symbol;
        this.errorType = errorType;
        this.rawData = rawData;
    }

    public String getSymbol() { return symbol; }
    public String getErrorType() { return errorType; }
    public Object getRawData() { return rawData; }
}
