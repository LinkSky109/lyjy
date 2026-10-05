package com.lhjypt.core.service.trade;

import com.lhjypt.core.mapper.PositionMapper;
import com.lhjypt.core.mapper.PositionReconciliationMapper;
import com.lhjypt.core.model.Position;
import com.lhjypt.core.model.trade.PositionDetail;
import com.lhjypt.core.model.trade.PositionReconciliation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * 系统持仓与柜台持仓对账。
 */
@Service
@RequiredArgsConstructor
public class PositionReconciliationService {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.00000001");

    private final PositionMapper positionMapper;
    private final PositionReconciliationMapper reconciliationMapper;
    private final TradeExecutionService tradeExecutionService;

    @Transactional
    public List<PositionReconciliation> reconcile(String accountId, String strategyId) {
        if (!tradeExecutionService.isAuthenticated()) {
            throw new IllegalStateException("交易网关未登录，无法执行持仓对账");
        }

        List<Position> systemPositions = strategyId == null || strategyId.isBlank()
                ? positionMapper.selectByAccount(accountId)
                : positionMapper.selectByAccountAndStrategy(accountId, strategyId);
        Map<String, BigDecimal> system = aggregateSystem(systemPositions);
        Map<String, BigDecimal> gateway = aggregateGateway(
                tradeExecutionService.queryPositions());

        Set<String> keys = new TreeSet<>();
        keys.addAll(system.keySet());
        keys.addAll(gateway.keySet());

        String reconNo = "PRECON"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        return keys.stream().map(key -> {
            String[] parts = key.split("\\|", 2);
            BigDecimal systemQuantity = system.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal gatewayQuantity = gateway.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal difference = systemQuantity.subtract(gatewayQuantity).abs();
            boolean matched = difference.compareTo(TOLERANCE) <= 0;

            PositionReconciliation record = new PositionReconciliation();
            record.setReconNo(reconNo + "-" + parts[0] + "-" + parts[1]);
            record.setAccountId(accountId);
            record.setStrategyId(strategyId);
            record.setSymbol(parts[0]);
            record.setDirection(parts[1]);
            record.setSystemQuantity(systemQuantity);
            record.setGatewayQuantity(gatewayQuantity);
            record.setDifference(difference);
            record.setStatus(matched ? "MATCHED" : "MISMATCHED");
            record.setReason(matched ? null
                    : "系统持仓 " + systemQuantity + "，柜台持仓 " + gatewayQuantity);
            record.setCreateTime(LocalDateTime.now());
            reconciliationMapper.insert(record);
            return record;
        }).toList();
    }

    public List<PositionReconciliation> recent(String accountId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return accountId == null || accountId.isBlank()
                ? reconciliationMapper.selectRecent(safeLimit)
                : reconciliationMapper.selectByAccount(accountId, safeLimit);
    }

    private static Map<String, BigDecimal> aggregateSystem(List<Position> positions) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Position position : positions) {
            String key = position.getSymbol() + "|" + position.getDirection();
            result.merge(key, zeroIfNull(position.getQuantity()), BigDecimal::add);
        }
        return result;
    }

    private static Map<String, BigDecimal> aggregateGateway(List<PositionDetail> positions) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (PositionDetail position : positions) {
            String direction = "LONG".equalsIgnoreCase(position.getDirection())
                    ? "LONG" : "SHORT";
            String key = position.getSymbol() + "|" + direction;
            result.merge(key, zeroIfNull(position.getTotalVolume()), BigDecimal::add);
        }
        return result;
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
