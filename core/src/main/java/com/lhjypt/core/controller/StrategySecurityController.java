package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.common.auth.UserContextHolder;
import com.lhjypt.core.model.strategy.StrategyAuditLog;
import com.lhjypt.core.model.strategy.StrategyCodeRequest;
import com.lhjypt.core.service.strategy.StrategySecurityService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 策略加密与沙箱 API（PRD §5.8）。
 */
@RestController
@RequestMapping("/api/strategy")
@RequiredArgsConstructor
public class StrategySecurityController {

    private final StrategySecurityService strategySecurityService;

    @PostMapping("/{strategyId}/code")
    public Result<Map<String, Object>> upload(@PathVariable String strategyId,
                                              @RequestBody StrategyCodeRequest request,
                                              HttpServletRequest servletRequest) {
        return Result.success(strategySecurityService.upload(
                strategyId, request.getName(), request.getSourceCode(),
                UserContextHolder.get(), clientIp(servletRequest)));
    }

    @PostMapping("/{strategyId}/code/download")
    public Result<Map<String, Object>> download(
            @PathVariable String strategyId,
            @RequestHeader(value = "X-Confirm-Token", required = false) String confirmToken,
            HttpServletRequest servletRequest) {
        return Result.success(strategySecurityService.download(
                strategyId, confirmToken, UserContextHolder.get(), clientIp(servletRequest)));
    }

    @PostMapping("/{strategyId}/run")
    public Result<Map<String, Object>> run(@PathVariable String strategyId,
                                           HttpServletRequest servletRequest) {
        return Result.success(strategySecurityService.run(
                strategyId, UserContextHolder.get(), clientIp(servletRequest)));
    }

    @PostMapping("/{strategyId}/stop")
    public Result<Map<String, Object>> stop(@PathVariable String strategyId,
                                            HttpServletRequest servletRequest) {
        return Result.success(strategySecurityService.stop(
                strategyId, UserContextHolder.get(), clientIp(servletRequest)));
    }

    @GetMapping("/sandbox/status/{strategyId}")
    public Result<Map<String, Object>> sandboxStatus(@PathVariable String strategyId) {
        return Result.success(strategySecurityService.sandboxStatus(
                strategyId, UserContextHolder.get()));
    }

    @GetMapping("/audit")
    public Result<List<StrategyAuditLog>> audit(
            @RequestParam(required = false) String strategyId,
            @RequestParam(required = false) String action) {
        return Result.success(strategySecurityService.audit(
                strategyId, action, UserContextHolder.get()));
    }

    @GetMapping("/{strategyId}/watermark")
    public Result<Map<String, Object>> watermark(@PathVariable String strategyId) {
        return Result.success(strategySecurityService.watermark(
                strategyId, UserContextHolder.get()));
    }

    @PostMapping("/trace")
    public Result<Map<String, Object>> trace(@RequestBody Map<String, String> body) {
        return Result.success(strategySecurityService.trace(
                body == null ? null : body.get("sourceCode"), UserContextHolder.get()));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
