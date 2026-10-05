package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.common.auth.ConfirmTokenService;
import com.lhjypt.core.model.gateway.GatewayInfo;
import com.lhjypt.core.model.gateway.GatewayRouteRule;
import com.lhjypt.core.model.trade.CTPConfig;
import com.lhjypt.core.service.trade.gateway.GatewayManagementService;
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
 * 多柜台多通道管理 API。
 */
@RestController
@RequestMapping("/api/gateway")
@RequiredArgsConstructor
public class GatewayController {

    private final GatewayManagementService gatewayService;
    private final ConfirmTokenService confirmTokenService;

    @PostMapping("/register")
    public Result<GatewayInfo> register(@RequestBody GatewayInfo info) {
        return Result.success(gatewayService.register(info));
    }

    @PostMapping("/{gatewayId}/connect")
    public Result<Void> connect(@PathVariable String gatewayId,
                                @RequestBody(required = false) CTPConfig config) {
        gatewayService.connect(gatewayId, config);
        return Result.success();
    }

    @PostMapping("/{gatewayId}/disconnect")
    public Result<Void> disconnect(@PathVariable String gatewayId) {
        gatewayService.disconnect(gatewayId);
        return Result.success();
    }

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        return Result.success(gatewayService.listGateways());
    }

    @GetMapping("/{gatewayId}/health")
    public Result<Map<String, Object>> health(@PathVariable String gatewayId) {
        return Result.success(gatewayService.health(gatewayId));
    }

    @PostMapping("/route")
    public Result<GatewayRouteRule> upsertRoute(@RequestBody GatewayRouteRule rule) {
        return Result.success(gatewayService.upsertRoute(rule));
    }

    @GetMapping("/route/list")
    public Result<List<GatewayRouteRule>> listRoutes() {
        return Result.success(gatewayService.listRoutes());
    }

    @PostMapping("/{gatewayId}/switch")
    public Result<GatewayRouteRule> switchAccount(
            @PathVariable String gatewayId,
            @RequestParam String accountId,
            @RequestParam(required = false) String fallbackId,
            @RequestParam(defaultValue = "admin") String operator,
            @RequestHeader(value = "X-Confirm-Token", required = false) String confirmToken) {
        if (!confirmTokenService.validate(confirmToken)) {
            return Result.error(409, "缺少或无效的二次确认令牌，请先调用 /api/auth/confirm-token 获取");
        }
        return Result.success(gatewayService.switchAccount(
                accountId, gatewayId, fallbackId, operator));
    }
}
