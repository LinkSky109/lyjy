package com.lhjypt.core.service.market.impl.real;

import com.lhjypt.core.config.DistributedLock;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
        DistributedLock distributedLock = mock(DistributedLock.class);
        // 锁模拟：直接执行回调节（否则 mock 默认不调用任务）
        when(distributedLock.execute(any(), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    invocation.getArgument(1, Runnable.class).run();
                    return true;
                });
        service = new RealMarketDataService(properties, klineSource, repository, distributedLock);
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

    @Test
    @DisplayName("定时刷新：拉取失败静默（不抛出，不影响调度）")
    void scheduledRefreshSwallowsFailure() throws Exception {
        properties.setRefreshIntervalMs(900000);
        doThrow(new java.io.IOException("network down"))
                .when(klineSource).fetchCandles("BTC_USDT", "1h", 200);

        service.scheduledRefresh(); // 不应抛出

        verify(repository, times(0)).saveBar(any(BarData.class));
    }

    @Test
    @DisplayName("定时刷新：正常路径使用默认参数并写入")
    void scheduledRefreshUsesDefaults() throws Exception {
        properties.setRefreshIntervalMs(900000);
        when(klineSource.fetchCandles("BTC_USDT", "1h", 200))
                .thenReturn(List.of(new RealKline(1791338400L, "1", "1", "1", "1", "1", "1")));
        when(repository.queryBars(any(), any(), any(), any(), eq(1000))).thenReturn(List.of());

        service.scheduledRefresh();

        verify(repository, times(1)).saveBar(any(BarData.class));
    }

    @Test
    @DisplayName("禁用或 interval=0 时定时刷新直接跳过")
    void scheduledRefreshSkippedWhenDisabled() throws Exception {
        properties.setRefreshIntervalMs(0);
        service.scheduledRefresh();
        verify(klineSource, times(0)).fetchCandles(any(), any(), anyInt());

        properties.setRefreshIntervalMs(900000);
        properties.setEnabled(false);
        service.scheduledRefresh();
        verify(klineSource, times(0)).fetchCandles(any(), any(), anyInt());
    }
}