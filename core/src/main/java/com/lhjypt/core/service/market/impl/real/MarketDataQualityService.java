package com.lhjypt.core.service.market.impl.real;

import com.lhjypt.core.model.market.BarData;
import com.lhjypt.core.service.market.MarketDataRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行情数据质量检测（P3-006）：缺口、异常值（OHLC 越界/非正量价）、重复时间戳。
 */
@Service
public class MarketDataQualityService {

    private final MarketDataRepository repository;

    public MarketDataQualityService(MarketDataRepository repository) {
        this.repository = repository;
    }

    public Map<String, Object> check(String symbol, String frequency, int limit) {
        List<BarData> bars = repository.queryBars(symbol, frequency,
                "2000-01-01T00:00:00", "2099-12-31T23:59:59", limit <= 0 ? 1000 : limit);
        // queryBars 返回 DESC -> 升序便于相邻比较
        List<BarData> ordered = new ArrayList<>(bars);
        ordered.sort((a, b) -> {
            if (a.getBarTime() == null || b.getBarTime() == null) {
                return 0;
            }
            return a.getBarTime().compareTo(b.getBarTime());
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("symbol", symbol);
        result.put("frequency", frequency);
        result.put("total", ordered.size());
        if (ordered.isEmpty()) {
            result.put("gaps", List.of());
            result.put("anomalies", List.of());
            result.put("gapCount", 0);
            result.put("anomalyCount", 0);
            result.put("note", "无数据（可 POST /api/market/real/refresh）");
            return result;
        }

        long periodMinutes = periodMinutes(frequency);
        List<Map<String, Object>> gaps = new ArrayList<>();
        List<Map<String, Object>> anomalies = new ArrayList<>();
        int duplicates = 0;

        for (int i = 0; i < ordered.size(); i++) {
            BarData bar = ordered.get(i);
            // 异常值
            if (bar.getLow() == null || bar.getHigh() == null || bar.getClose() == null
                    || bar.getOpen() == null) {
                anomalies.add(anomaly(bar, "存在空值"));
            } else {
                if (bar.getHigh().compareTo(bar.getLow()) < 0) {
                    anomalies.add(anomaly(bar, "high < low"));
                }
                if (bar.getClose().compareTo(bar.getLow()) < 0
                        || bar.getClose().compareTo(bar.getHigh()) > 0) {
                    anomalies.add(anomaly(bar, "close 超出 [low, high]"));
                }
                if (bar.getOpen().compareTo(bar.getLow()) < 0
                        || bar.getOpen().compareTo(bar.getHigh()) > 0) {
                    anomalies.add(anomaly(bar, "open 超出 [low, high]"));
                }
                if (bar.getClose().signum() <= 0) {
                    anomalies.add(anomaly(bar, "close <= 0"));
                }
            }
            if (bar.getVolume() <= 0) {
                anomalies.add(anomaly(bar, "volume <= 0"));
            }
            // 缺口 / 重复
            if (i > 0) {
                BarData prev = ordered.get(i - 1);
                if (prev.getBarTime() == null || bar.getBarTime() == null) {
                    continue;
                }
                long diffMinutes = java.time.Duration.between(
                        prev.getBarTime(), bar.getBarTime()).toMinutes();
                if (diffMinutes == 0) {
                    duplicates++;
                } else if (diffMinutes > periodMinutes && periodMinutes > 0) {
                    Map<String, Object> gap = new LinkedHashMap<>();
                    gap.put("from", prev.getBarTime());
                    gap.put("to", bar.getBarTime());
                    gap.put("missingBars", Math.max(1, (int) (diffMinutes / periodMinutes) - 1));
                    if (gaps.size() < 20) {
                        gaps.add(gap);
                    }
                }
            }
        }

        result.put("gapCount", gaps.size());
        result.put("gaps", gaps);
        result.put("anomalyCount", anomalies.size());
        result.put("anomalies", anomalies.size() > 20 ? anomalies.subList(0, 20) : anomalies);
        result.put("duplicates", duplicates);
        result.put("earliest", ordered.get(0).getBarTime());
        result.put("latest", ordered.get(ordered.size() - 1).getBarTime());
        return result;
    }

    private static Map<String, Object> anomaly(BarData bar, String reason) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("barTime", bar.getBarTime());
        item.put("open", bar.getOpen());
        item.put("high", bar.getHigh());
        item.put("low", bar.getLow());
        item.put("close", bar.getClose());
        item.put("volume", bar.getVolume());
        item.put("reason", reason);
        return item;
    }

    static long periodMinutes(String frequency) {
        if (frequency == null) {
            return 60;
        }
        return switch (frequency) {
            case "1m" -> 1;
            case "5m" -> 5;
            case "15m" -> 15;
            case "30m" -> 30;
            case "1h" -> 60;
            case "4h" -> 240;
            case "1d" -> 1440;
            default -> 60;
        };
    }
}