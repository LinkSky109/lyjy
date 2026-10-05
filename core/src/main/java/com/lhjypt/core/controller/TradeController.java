package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.model.Order;
import com.lhjypt.core.model.trade.*;
import com.lhjypt.core.service.trade.TradeApplicationService;
import com.lhjypt.core.service.trade.TradeExecutionService;
import com.lhjypt.core.service.trade.TradeQueryService;
import com.lhjypt.core.model.Position;
import com.lhjypt.core.model.FundAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 交易执行API - CTP柜台对接
 */
@RestController
@RequestMapping("/api/trade")
@RequiredArgsConstructor
public class TradeController {

    private final TradeExecutionService tradeService;
    private final TradeApplicationService tradeApplicationService;
    private final TradeQueryService tradeQueryService;

    /**
     * 连接并登录CTP柜台
     */
    @PostMapping("/connect")
    public Result<Void> connect(@RequestBody CTPConfig config) {
        tradeService.connect(config);
        return Result.success();
    }

    /**
     * 断开连接
     */
    @PostMapping("/disconnect")
    public Result<Void> disconnect() {
        tradeService.disconnect();
        return Result.success();
    }

    /**
     * 连接状态
     */
    @GetMapping("/status")
    public Result<java.util.Map<String, Object>> status() {
        return Result.success(java.util.Map.of(
                "connected", tradeService.isConnected(),
                "authenticated", tradeService.isAuthenticated(),
                "mode", tradeService.getMode()
        ));
    }

    /**
     * 统一下单入口。
     */
    @PostMapping("/orders")
    public Result<TradeSubmissionResult> submitOrder(@RequestBody TradeRequest request) {
        return Result.success(tradeApplicationService.submit(request));
    }

    /**
     * 兼容旧下单入口，内部仍走统一编排链路。
     */
    @Deprecated
    @PostMapping("/order")
    public Result<String> placeOrder(@RequestBody TradeRequest request) {
        return Result.success(tradeApplicationService.submit(request).orderId());
    }

    /**
     * 按平台订单号查询。
     */
    @GetMapping("/orders/{orderId}")
    public Result<Order> queryPlatformOrder(@PathVariable String orderId) {
        return Result.success(tradeApplicationService.getOrder(orderId));
    }

    /**
     * 按平台订单号撤单。
     */
    @DeleteMapping("/orders/{orderId}")
    public Result<Order> cancelPlatformOrder(@PathVariable String orderId,
                                             @RequestParam(defaultValue = "用户撤单") String reason) {
        return Result.success(tradeApplicationService.cancel(orderId, reason));
    }

    /**
     * 平台账本持仓。
     */
    @GetMapping("/platform/positions")
    public Result<List<Position>> platformPositions(
            @RequestParam String accountId,
            @RequestParam(required = false) String strategyId) {
        return Result.success(tradeQueryService.positions(accountId, strategyId));
    }

    /**
     * 平台账本账户。
     */
    @GetMapping("/platform/account")
    public Result<FundAccount> platformAccount(@RequestParam String accountId) {
        return Result.success(tradeQueryService.account(accountId));
    }

    /**
     * 撤单
     */
    @DeleteMapping("/order/{exchangeOrderId}")
    public Result<Boolean> cancelOrder(@PathVariable String exchangeOrderId,
                                       @RequestParam String symbol) {
        return Result.success(tradeService.cancelOrder(exchangeOrderId, symbol));
    }

    /**
     * 查询订单
     */
    @GetMapping("/order/{exchangeOrderId}")
    public Result<OrderReport> queryOrder(@PathVariable String exchangeOrderId) {
        return Result.success(tradeService.queryOrder(exchangeOrderId));
    }

    /**
     * 查询成交记录
     */
    @GetMapping("/trades")
    public Result<List<TradeReport>> queryTrades() {
        return Result.success(tradeService.queryTrades());
    }

    /**
     * 查询持仓
     */
    @GetMapping("/positions")
    public Result<List<PositionDetail>> queryPositions() {
        return Result.success(tradeService.queryPositions());
    }

    /**
     * 查询账户资金
     */
    @GetMapping("/account")
    public Result<AccountInfo> queryAccount() {
        return Result.success(tradeService.queryAccount());
    }
}
