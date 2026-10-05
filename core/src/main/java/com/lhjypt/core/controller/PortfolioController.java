package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.common.auth.ConfirmTokenService;
import com.lhjypt.core.common.auth.UserContext;
import com.lhjypt.core.common.auth.UserContextHolder;
import com.lhjypt.core.model.portfolio.Portfolio;
import com.lhjypt.core.model.portfolio.PortfolioPosition;
import com.lhjypt.core.model.portfolio.PortfolioRebalanceJob;
import com.lhjypt.core.model.portfolio.RebalancePreview;
import com.lhjypt.core.service.portfolio.PortfolioService;
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
 * 组合管理与再平衡 API。
 */
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final ConfirmTokenService confirmTokenService;

    @PostMapping
    public Result<Portfolio> create(@RequestBody Portfolio portfolio) {
        UserContext context = UserContextHolder.get();
        return Result.success(portfolioService.create(portfolio, context.userId()));
    }

    @GetMapping("/list")
    public Result<List<Portfolio>> list() {
        UserContext context = UserContextHolder.get();
        boolean admin = context.hasAnyRole(UserContext.ROLE_ADMIN);
        return Result.success(portfolioService.list(context.userId(), admin));
    }

    @PostMapping("/{portfolioId}/positions")
    public Result<List<PortfolioPosition>> setPositions(
            @PathVariable String portfolioId,
            @RequestBody List<PortfolioPosition> positions) {
        return Result.success(portfolioService.setPositions(
                portfolioId, UserContextHolder.get().userId(), positions));
    }

    @GetMapping("/{portfolioId}")
    public Result<Map<String, Object>> detail(@PathVariable String portfolioId) {
        UserContext context = UserContextHolder.get();
        return Result.success(portfolioService.detail(
                portfolioId, context.userId(),
                context.hasAnyRole(UserContext.ROLE_ADMIN)));
    }

    @PostMapping("/{portfolioId}/rebalance")
    public Result<PortfolioRebalanceJob> trigger(
            @PathVariable String portfolioId,
            @RequestParam(defaultValue = "MANUAL") String triggerType,
            @RequestParam(defaultValue = "MANUAL") String executeMode) {
        return Result.success(portfolioService.trigger(
                portfolioId, triggerType, executeMode,
                UserContextHolder.get().userId()));
    }

    @PostMapping("/rebalance/{jobId}/preview")
    public Result<RebalancePreview> preview(@PathVariable String jobId) {
        UserContext context = UserContextHolder.get();
        return Result.success(portfolioService.preview(
                jobId, context.userId(),
                context.hasAnyRole(UserContext.ROLE_ADMIN)));
    }

    @PostMapping("/rebalance/{jobId}/confirm")
    public Result<PortfolioRebalanceJob> confirm(
            @PathVariable String jobId,
            @RequestHeader(value = "X-Confirm-Token", required = false)
            String confirmToken) {
        if (!confirmTokenService.validate(confirmToken)) {
            return Result.error(409, "执行再平衡需要有效的二次确认令牌");
        }
        UserContext context = UserContextHolder.get();
        return Result.success(portfolioService.confirm(
                jobId, context.userId(),
                context.hasAnyRole(UserContext.ROLE_ADMIN)));
    }
}
