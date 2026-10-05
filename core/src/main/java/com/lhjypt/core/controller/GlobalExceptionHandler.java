package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.common.exception.TradeRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将业务异常转换为统一响应结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TradeRejectedException.class)
    public Result<Object> handleTradeRejected(TradeRejectedException error) {
        int code = switch (error.getCode()) {
            case "VALIDATION_FAILED", "PRICE_REQUIRED", "INVALID_QUANTITY" -> 400;
            case "GATEWAY_UNAVAILABLE" -> 503;
            default -> 422;
        };
        String message = error.getViolations().isEmpty()
                ? error.getMessage()
                : error.getMessage() + ": " + String.join("; ", error.getViolations());
        log.warn("交易请求被拒绝: code={} message={}", error.getCode(), message);
        return Result.error(code, message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Object> handleIllegalArgument(IllegalArgumentException error) {
        return Result.error(400, error.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public Result<Object> handleIllegalState(IllegalStateException error) {
        return Result.error(409, error.getMessage());
    }
}
