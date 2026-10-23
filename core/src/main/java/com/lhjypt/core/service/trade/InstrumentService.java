package com.lhjypt.core.service.trade;

import com.lhjypt.core.config.InstrumentProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 合约元数据查询。未配置的合约返回 empty，由前端回退到保守校验。
 */
@Service
public class InstrumentService {

    private final InstrumentProperties properties;

    public InstrumentService(InstrumentProperties properties) {
        this.properties = properties;
    }

    public Optional<InstrumentView> find(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        InstrumentProperties.Spec spec = properties.getContracts().get(symbol.trim());
        return spec == null ? Optional.empty() : Optional.of(toView(symbol.trim(), spec));
    }

    public List<InstrumentView> list() {
        return properties.getContracts().entrySet().stream()
                .map(entry -> toView(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static InstrumentView toView(String symbol, InstrumentProperties.Spec spec) {
        return new InstrumentView(
                symbol,
                spec.getName(),
                spec.getExchange(),
                spec.getInstrumentType(),
                spec.getPriceTick(),
                spec.getContractMultiplier(),
                spec.getCurrency(),
                spec.getMinQty(),
                spec.getMaxQty(),
                spec.getQtyStep(),
                spec.getLimitUp(),
                spec.getLimitDown(),
                spec.isTrading());
    }

    public record InstrumentView(
            String symbol,
            String name,
            String exchange,
            String instrumentType,
            BigDecimal priceTick,
            BigDecimal contractMultiplier,
            String currency,
            BigDecimal minQty,
            BigDecimal maxQty,
            BigDecimal qtyStep,
            BigDecimal limitUp,
            BigDecimal limitDown,
            boolean trading) {
    }
}
