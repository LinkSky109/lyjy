package com.lhjypt.core.service.trade;

import com.lhjypt.core.common.exception.TradeRejectedException;
import com.lhjypt.core.model.Order;
import com.lhjypt.core.model.order.CreateOrderCommand;
import com.lhjypt.core.model.risk.RiskCheckResult;
import com.lhjypt.core.model.trade.TradeRequest;
import com.lhjypt.core.model.trade.TradeSubmissionResult;
import com.lhjypt.core.service.fund.FundReservationService;
import com.lhjypt.core.service.order.OrderManagementService;
import com.lhjypt.core.service.order.OrderStatus;
import com.lhjypt.core.service.risk.PreTradeRiskService;
import com.lhjypt.core.service.risk.RiskRule;
import com.lhjypt.core.service.risk.TradingRiskContextFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * 统一交易用例入口。
 *
 * 本地事务只覆盖幂等、OMS 建单和资金冻结；外部网关调用在事务之外执行。
 */
@Service
@RequiredArgsConstructor
public class TradeApplicationService {

    private static final Logger log = LoggerFactory.getLogger(TradeApplicationService.class);
    private static final Set<String> DIRECTIONS = Set.of("BUY", "SELL");
    private static final Set<String> OFFSET_FLAGS = Set.of(
            "OPEN", "CLOSE", "CLOSE_TODAY", "CLOSE_YESTERDAY");
    private static final Set<String> ORDER_TYPES = Set.of("LIMIT", "MARKET");

    private final PreTradeRiskService preTradeRiskService;
    private final TradingRiskContextFactory riskContextFactory;
    private final OrderManagementService orderManagementService;
    private final FundReservationService fundReservationService;
    private final TradeCostCalculator costCalculator;
    private final TradeExecutionService tradeExecutionService;

    public TradeSubmissionResult submit(TradeRequest request) {
        validate(request);

        RiskRule.RiskContext context = riskContextFactory.create(request);
        RiskCheckResult riskResult = preTradeRiskService.check(request, context);
        if (!riskResult.isPassed()) {
            List<String> violations = riskResult.getViolations().stream()
                    .map(RiskCheckResult.RuleViolation::message)
                    .toList();
            throw new TradeRejectedException(
                    "RISK_REJECTED", "订单未通过事前风控", violations);
        }

        Order order = orderManagementService.createOrder(toCommand(request));
        if (!OrderStatus.PENDING.getCode().equals(order.getStatus())) {
            return toResult(order, true);
        }

        TradeCostCalculator.TradeCost cost;
        try {
            cost = costCalculator.estimate(request);
            fundReservationService.reserve(order, request, cost);
        } catch (Exception error) {
            orderManagementService.reject(order.getOrderId(), "FUND_RESERVE_FAILED", error.getMessage());
            throw new TradeRejectedException(
                    "INSUFFICIENT_FUNDS", "资金冻结失败: " + error.getMessage());
        }

        request.setClientOrderId(order.getOrderId());
        try {
            String brokerOrderRef = tradeExecutionService.placeOrder(request);
            order = orderManagementService.markSubmitted(order.getOrderId(), brokerOrderRef);
            log.info("订单已提交: orderId={} brokerRef={} symbol={}",
                    order.getOrderId(), brokerOrderRef, order.getSymbol());
            return toResult(order, false);
        } catch (Exception error) {
            fundReservationService.releaseRemaining(order.getOrderId());
            orderManagementService.reject(order.getOrderId(), "GATEWAY_UNAVAILABLE", error.getMessage());
            throw new TradeRejectedException(
                    "GATEWAY_UNAVAILABLE", "柜台提交失败: " + error.getMessage());
        }
    }

    public Order getOrder(String orderId) {
        return orderManagementService.getOrderByOrderId(orderId);
    }

    public Order cancel(String orderId, String reason) {
        Order order = orderManagementService.getOrderByOrderId(orderId);
        if (OrderStatus.fromCode(order.getStatus()).isTerminal()) {
            return order;
        }
        if (order.getExchangeOrderId() == null || order.getExchangeOrderId().isBlank()) {
            throw new IllegalStateException("订单尚未收到柜台确认，暂不能撤单: " + orderId);
        }
        boolean canceled = tradeExecutionService.cancelOrder(
                order.getExchangeOrderId(), order.getSymbol());
        if (!canceled) {
            throw new IllegalStateException("柜台撤单失败: " + orderId);
        }
        Order updated = orderManagementService.cancel(orderId, reason);
        fundReservationService.releaseRemaining(orderId);
        return updated;
    }

    private void validate(TradeRequest request) {
        if (request == null) {
            throw new TradeRejectedException("VALIDATION_FAILED", "下单请求不能为空");
        }
        requireText(request.getAccountId(), "accountId");
        requireText(request.getStrategyId(), "strategyId");
        requireText(request.getSymbol(), "symbol");
        requireText(request.getIdempotencyKey(), "idempotencyKey");
        requireText(request.getDirection(), "direction");
        requireText(request.getOffsetFlag(), "offsetFlag");
        requireText(request.getOrderType(), "orderType");

        if (!DIRECTIONS.contains(request.getDirection().toUpperCase())) {
            throw new TradeRejectedException("VALIDATION_FAILED", "direction 仅支持 BUY/SELL");
        }
        if (!OFFSET_FLAGS.contains(request.getOffsetFlag().toUpperCase())) {
            throw new TradeRejectedException(
                    "VALIDATION_FAILED", "offsetFlag 仅支持 OPEN/CLOSE/CLOSE_TODAY/CLOSE_YESTERDAY");
        }
        if (!ORDER_TYPES.contains(request.getOrderType().toUpperCase())) {
            throw new TradeRejectedException(
                    "VALIDATION_FAILED", "orderType 仅支持 LIMIT/MARKET");
        }
        if ("LIMIT".equalsIgnoreCase(request.getOrderType()) && request.getPrice() == null) {
            throw new TradeRejectedException("VALIDATION_FAILED", "限价单必须指定价格");
        }
        if (request.getPrice() == null) {
            throw new TradeRejectedException(
                    "PRICE_REQUIRED", "当前版本暂不支持无参考价的市价单");
        }
        if (request.getQuantity() == null
                || request.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new TradeRejectedException(
                    "VALIDATION_FAILED", "quantity 必须大于0");
        }
    }

    private CreateOrderCommand toCommand(TradeRequest request) {
        CreateOrderCommand command = new CreateOrderCommand();
        command.setIdempotentKey(request.getIdempotencyKey());
        command.setStrategyId(request.getStrategyId());
        command.setAccountId(request.getAccountId());
        command.setSymbol(request.getSymbol());
        command.setExchange(request.getExchange());
        command.setDirection(request.getDirection().toUpperCase());
        command.setOffsetFlag(request.getOffsetFlag().toUpperCase());
        command.setOrderType(request.getOrderType().toUpperCase());
        command.setPrice(request.getPrice());
        command.setQuantity(request.getQuantity());
        return command;
    }

    private TradeSubmissionResult toResult(Order order, boolean replay) {
        return new TradeSubmissionResult(
                order.getOrderId(),
                order.getStatus(),
                order.getBrokerOrderRef(),
                tradeExecutionService.getMode(),
                replay);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new TradeRejectedException(
                    "VALIDATION_FAILED", field + " 不能为空");
        }
    }
}
