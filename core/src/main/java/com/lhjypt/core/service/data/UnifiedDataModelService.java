package com.lhjypt.core.service.data;

import com.lhjypt.core.model.data.UnifiedMarketData;
import com.lhjypt.core.model.market.TickData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一数据模型服务
 * 将不同行情源（CTP/Mock/Replay/第三方）的原始数据转换为统一的UnifiedMarketData
 * 抹平格式差异，统一字段命名、单位、精度
 */
@Service
@RequiredArgsConstructor
public class UnifiedDataModelService {
    private static final Logger log = LoggerFactory.getLogger(UnifiedDataModelService.class);


    private final DataCleaningService cleaningService;
    private final AdjustmentService adjustmentService;

    /**
     * 将TickData转换为统一模型
     */
    public UnifiedMarketData fromTick(TickData tick, String source) {
        UnifiedMarketData unified = new UnifiedMarketData();
        unified.setSymbol(tick.getSymbol());
        unified.setSource(source);
        unified.setLastPrice(tick.getLastPrice());
        unified.setPreClose(tick.getPreClose());
        unified.setOpen(tick.getOpen());
        unified.setHigh(tick.getHigh());
        unified.setLow(tick.getLow());
        unified.setVolume(tick.getVolume());
        unified.setTurnover(tick.getTurnover());
        unified.setOpenInterest(tick.getOpenInterest());
        unified.setMarketTime(tick.getTickTime());
        unified.setRecvTime(tick.getRecvTime());

        // 五档买卖盘转换
        Map<Integer, BigDecimal> bidPrices = new HashMap<>();
        Map<Integer, Long> bidVolumes = new HashMap<>();
        Map<Integer, BigDecimal> askPrices = new HashMap<>();
        Map<Integer, Long> askVolumes = new HashMap<>();

        bidPrices.put(1, tick.getBidPrice1());
        bidPrices.put(2, tick.getBidPrice2());
        bidPrices.put(3, tick.getBidPrice3());
        bidPrices.put(4, tick.getBidPrice4());
        bidPrices.put(5, tick.getBidPrice5());
        bidVolumes.put(1, tick.getBidVolume1());
        bidVolumes.put(2, tick.getBidVolume2());
        bidVolumes.put(3, tick.getBidVolume3());
        bidVolumes.put(4, tick.getBidVolume4());
        bidVolumes.put(5, tick.getBidVolume5());

        askPrices.put(1, tick.getAskPrice1());
        askPrices.put(2, tick.getAskPrice2());
        askPrices.put(3, tick.getAskPrice3());
        askPrices.put(4, tick.getAskPrice4());
        askPrices.put(5, tick.getAskPrice5());
        askVolumes.put(1, tick.getAskVolume1());
        askVolumes.put(2, tick.getAskVolume2());
        askVolumes.put(3, tick.getAskVolume3());
        askVolumes.put(4, tick.getAskVolume4());
        askVolumes.put(5, tick.getAskVolume5());

        unified.setBidPrices(bidPrices);
        unified.setBidVolumes(bidVolumes);
        unified.setAskPrices(askPrices);
        unified.setAskVolumes(askVolumes);

        // 推断交易所和品种类型
        inferExchangeAndType(unified);

        return unified;
    }

    /**
     * 完整处理流程：转换 → 清洗 → 复权
     */
    public UnifiedMarketData process(TickData tick, String source) {
        // 1. 转换为统一模型
        UnifiedMarketData unified = fromTick(tick, source);

        // 2. 数据清洗
        unified = cleaningService.clean(unified);

        // 3. 清洗通过才进行复权
        if (cleaningService.isUsable(unified)) {
            // 复权在查询历史数据时应用，实时行情保持原价
            unified.setAdjusted(false);
        }

        return unified;
    }

    /**
     * 根据标的代码推断交易所和品种类型
     */
    private void inferExchangeAndType(UnifiedMarketData data) {
        String symbol = data.getSymbol().toUpperCase();

        // 期货品种推断
        if (symbol.matches("^(RB|CU|AL|ZN|PB|NI|SN|AU|AG|HC|SS|WR|RU|NR|BU|FU|LU|PG|V|L|PP|J|JM|I|SF|SM|FG|SA|MA|TA|PF|PR|SH|SP|EG|EB|CS|A|B|M|Y|P|C|JD|RR|AP|CF|CY|SR|OI|RM|PK|UR|T|TF|TS|TL|IF|IH|IC|IM|IO|MO|HO)[0-9]{3,4}$")) {
            data.setInstrumentType("FUTURE");
            if (symbol.startsWith("RB") || symbol.startsWith("HC") || symbol.startsWith("SS")) {
                data.setExchange("SHFE");
            } else if (symbol.startsWith("CU") || symbol.startsWith("AL") || symbol.startsWith("AU") || symbol.startsWith("AG")) {
                data.setExchange("SHFE");
            } else if (symbol.startsWith("IF") || symbol.startsWith("IH") || symbol.startsWith("IC") || symbol.startsWith("IM")) {
                data.setExchange("CFFEX");
            } else if (symbol.startsWith("SR") || symbol.startsWith("CF") || symbol.startsWith("TA") || symbol.startsWith("MA")) {
                data.setExchange("CZCE");
            } else if (symbol.startsWith("M") || symbol.startsWith("Y") || symbol.startsWith("A") || symbol.startsWith("B")) {
                data.setExchange("DCE");
            } else {
                data.setExchange("UNKNOWN");
            }
        }
        // 股票推断（6位数字）
        else if (symbol.matches("^[0-9]{6}$")) {
            data.setInstrumentType("STOCK");
            if (symbol.startsWith("6")) {
                data.setExchange("SSE");
            } else if (symbol.startsWith("0") || symbol.startsWith("3")) {
                data.setExchange("SZSE");
            } else if (symbol.startsWith("8") || symbol.startsWith("4")) {
                data.setExchange("BSE");
            }
        } else {
            data.setExchange("UNKNOWN");
            data.setInstrumentType("UNKNOWN");
        }
    }
}
