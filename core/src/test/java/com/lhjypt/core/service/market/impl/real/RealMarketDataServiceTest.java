package com.lhjypt.core.service.market.impl.real;

import com.lhjypt.core.config.RealMarketProperties;
import com.lhjypt.core.model.market.BarData;
import com.lhjypt.core.service.market.MarketDataRepository;
import com.lhjypt.core.service.market.impl.real.GateioKlineSource.RealKline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealMarketDataServiceTest {

    private RealMarketProperties properties;
    private GateioKlineSource klineSource;
    private MarketDataRepository repository;
    private RealMarketDataService service;

    @BeforeEach
    void setUp() {
        properties = new RealMarketProperties();
        klineSource = mock(GateioKlineSource.class);
        repository = mock(MarketDataRepository.class);
        service = new RealMarketDataService(properties, klineSource, repository);
    }

    @Test
    @DisplayName("刷新：K 线转 BarData 写库并按时间戳去重")
    void refreshMapsAndDeduplicates() throws Exception {
        long ts1 = 1791338400L;
        long ts2 = 1791342000L;
        when(klineSource.fetchCandles("BTC_USDT", "1h", 200)).thenReturn(List.of(
                new RealKline(ts1, "124544690", "83862.5", "84535.7", "83584.9", "84390", "1484.17"),
                new RealKline(ts2, "42038512", "84155", "84247", "83621", "84247", "499.55")));

        LocalDateTime t1 = Instant.ofEpochSecond(ts1).atZone(ZoneId.systemDefault()).toLocalDateTime();
        BarData existing = new BarData();
        existing.setBarTime(t1);
        when(repository.queryBars(eq("BTC_USDT"), eq("1h"), any(), any(), eq(1000)))
                .thenReturn(List.of(existing));

        int inserted = service.refresh("BTC_USDT", "1h", 200);

        assertThat(inserted).isEqualTo(1);
        ArgumentCaptor<BarData> captor = ArgumentCaptor.forClass(BarData.class);
        verify(repository, times(1)).saveBar(captor.capture());
        BarData bar = captor.getValue();
        assertThat(bar.getSymbol()).isEqualTo("BTC_USDT");
        assertThat(bar.getFrequency()).isEqualTo("1h");
        assertThat(bar.getClose()).isEqualByComparingTo("84247");
        assertThat(bar.getVolume()).isEqualTo(499);
    }

    @Test
    @DisplayName("禁用时刷新被拒绝")
    void refreshRejectedWhenDisabled() throws Exception {
        properties.setEnabled(false);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.refresh("BTC_USDT", "1h", 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未启用");
    }

    @Test
    @DisplayName("默认值兜底：currencyPair/interval/limit 缺省用配置")
    void defaultsApplied() throws Exception {
        properties.setCurrencyPair("ETH_USDT");
        when(klineSource.fetchCandles("ETH_USDT", "1h", 200))
                .thenReturn(List.of(new RealKline(1791338400L, "1", "1", "1", "1", "1", "1")));
        when(repository.queryBars(any(), any(), any(), any(), eq(1000))).thenReturn(List.of());

        int inserted = service.refresh(null, null, null);

        assertThat(inserted).isEqualTo(1);
    }
}