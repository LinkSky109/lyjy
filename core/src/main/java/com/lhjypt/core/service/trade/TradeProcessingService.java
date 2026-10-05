package com.lhjypt.core.service.trade;

import com.lhjypt.core.mapper.TradeRecordMapper;
import com.lhjypt.core.model.Order;
import com.lhjypt.core.model.trade.TradeRecord;
import com.lhjypt.core.model.trade.TradeReport;
import com.lhjypt.core.service.fund.FundManagementService;
import com.lhjypt.core.service.fund.FundReservationService;
import com.lhjypt.core.service.order.OrderManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成交回报到 OMS、持仓和资金的统一处理。
 */
@Service
@RequiredArgsConstructor
public class TradeProcessingService {

    private final TradeRecordMapper tradeRecordMapper;
    private final OrderManagementService orderManagementService;
    private final PositionService positionService;
    private final FundReservationService fundReservationService;
    private final FundManagementService fundManagementService;
    private final TradeCostCalculator costCalculator;

    @Transactional
    public TradeRecord process(Order order, TradeReport report) {
        TradeRecord existing = tradeRecordMapper.selectByTradeId(
                report.getGatewayId(), report.getGatewaySessionId(), report.getTradeId());
        if (existing != null) {
            return existing;
        }

        TradeRecord trade = toTradeRecord(order, report);
        tradeRecordMapper.insert(trade);

        orderManagementService.applyFill(
                order.getOrderId(), report.getVolume(), report.getPrice());

        BigDecimal realizedPnl = positionService.applyFill(order, trade);
        TradeCostCalculator.TradeCost cost = costCalculator.calculate(
                trade.getOffsetFlag(), trade.getPrice(), trade.getQuantity());
        trade.setCommission(cost.commission());
        fundReservationService.consumeFill(
                order.getOrderId(), cost.margin(), cost.commission());
        fundManagementService.applyRealizedPnl(
                order.getAccountId(), realizedPnl, trade.getTradeId(), trade.getSymbol());
        trade.setRealizedPnl(realizedPnl);
        tradeRecordMapper.updateById(trade);
        return trade;
    }

    private TradeRecord toTradeRecord(Order order, TradeReport report) {
        TradeRecord trade = new TradeRecord();
        trade.setTradeId(report.getTradeId());
        trade.setGatewayId(report.getGatewayId());
        trade.setGatewaySessionId(report.getGatewaySessionId());
        trade.setOrderId(order.getOrderId());
        trade.setExchangeOrderId(report.getExchangeOrderId());
        trade.setAccountId(order.getAccountId());
        trade.setStrategyId(order.getStrategyId());
        trade.setSymbol(report.getSymbol());
        trade.setExchange(report.getExchange());
        trade.setDirection(report.getDirection());
        trade.setOffsetFlag(report.getOffsetFlag());
        trade.setPrice(report.getPrice());
        trade.setQuantity(report.getVolume());
        trade.setTurnover(report.getTurnover());
        trade.setCommission(BigDecimal.ZERO);
        trade.setRealizedPnl(BigDecimal.ZERO);
        trade.setTradeTime(report.getTradeTime());
        trade.setTradingDay(report.getTradingDay());
        trade.setCreateTime(LocalDateTime.now());
        return trade;
    }
}
