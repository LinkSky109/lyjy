package com.lhjypt.core.service.market.impl.real;

import com.lhjypt.core.model.market.BarData;
import com.lhjypt.core.service.market.MarketDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketDataQualityServiceTest {

    private MarketDataRepository repository;
    private MarketDataQualityService service;

    @BeforeEach
    void setUp() {
        repository = mock(MarketDataRepository.class);
        service = new MarketDataQualityService(repository);
    }

    private static BarData bar(String time, String o, String h, String l, String c, long volume) {
        BarData bar = new BarData();
        bar.setSymbol("BTC_USDT");
        bar.setFrequency("1h");
        bar.setOpen(new BigDecimal(o));
        bar.setHigh(new BigDecimal(h));
        bar.setLow(new BigDecimal(l));
        bar.setClose(new BigDecimal(c));
        bar.setVolume(volume);
        bar.setBarTime(LocalDateTime.parse(time));
        return bar;
    }

    @Test
    @DisplayName("检测缺口与重复（1h 周期）")
    void detectsGapsAndDuplicates() {
        // 11:00 -> 13:00 缺 12:00；13:00 后重复 13:00
        when(repository.queryBars(eq("BTC_USDT"), eq("1h"), any(), any(), eq(1000)))
                .thenReturn(List.of(
                        bar("2026-10-08T11:00:00", "1", "2", "0.5", "1.5", 10),
                        bar("2026-10-08T13:00:00", "1", "2", "0.5", "1.5", 10),
                        bar("2026-10-08T13:00:00", "1", "2", "0.5", "1.5", 10)));

        Map<String, Object> result = service.check("BTC_USDT", "1h", 1000);

        assertThat(result.get("total")).isEqualTo(3);
        assertThat((int) result.get("gapCount")).isEqualTo(1);
        assertThat(result.get("duplicates")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> gaps = (List<Map<String, Object>>) result.get("gaps");
        assertThat(gaps.get(0).get("missingBars")).isEqualTo(1);
    }

    @Test
    @DisplayName("检测异常值（OHLC 越界 / 非正量价）")
    void detectsAnomalies() {
        when(repository.queryBars(eq("BTC_USDT"), eq("1h"), any(), any(), eq(1000)))
                .thenReturn(List.of(
                        bar("2026-10-08T10:00:00", "1", "0.5", "2", "1.5", 10),   // high<low
                        bar("2026-10-08T11:00:00", "1", "2", "0.5", "3", 10),       // close 越界
                        bar("2026-10-08T12:00:00", "1", "2", "0.5", "1.5", 0)));    // volume<=0

        Map<String, Object> result = service.check("BTC_USDT", "1h", 1000);

        assertThat((int) result.get("anomalyCount")).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("空数据给出提示")
    void emptyData() {
        when(repository.queryBars(any(), any(), any(), any(), any(Integer.class))).thenReturn(List.of());
        Map<String, Object> result = service.check("BTC_USDT", "1h", 1000);
        assertThat(result.get("total")).isEqualTo(0);
        assertThat(result.get("note")).asString().contains("无数据");
    }

    @Test
    @DisplayName("周期映射")
    void periodMinutesMapping() {
        assertThat(MarketDataQualityService.periodMinutes("1m")).isEqualTo(1);
        assertThat(MarketDataQualityService.periodMinutes("1h")).isEqualTo(60);
        assertThat(MarketDataQualityService.periodMinutes("1d")).isEqualTo(1440);
    }
}