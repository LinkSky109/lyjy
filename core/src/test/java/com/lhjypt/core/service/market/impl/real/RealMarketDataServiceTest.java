package com.lhjypt.core.service.market.impl.real;

import com.lhjypt.core.config.DistributedLock;
import com.lhjypt.core.config.RealMarketProperties;
import com.lhjypt.core.model.market.BarData;
import com.lhjypt.core.service.market.MarketDataRepository;
import com.lhjypt.core.service.market.source.MarketCandle;
import com.lhjypt.core.service.market.source.MarketDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealMarketDataServiceTest {

    private RealMarketProperties properties;
    private MarketDataRepository repository;
    private DistributedLock lock;

    /** 可编程的假源。 */
    private static MarketDataSource source(String name, boolean ok, List<MarketCandle> candles) {
        return new MarketDataSource() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public List<MarketCandle> fetch(String pair, String interval, int limit) {
                if (!ok) {
                    throw new IllegalStateException(name + " 不可用");
                }
                return candles;
            }
        };
    }

    private static MarketCandle candle(long ts, String close) {
        return new MarketCandle(ts, close, close, close, close, "10", "100");
    }

    @BeforeEach
    void setUp() {
        properties = new RealMarketProperties();
        properties.setSources(List.of("gateio", "backup"));
        properties.setMaxAttempts(1);
        repository = mock(MarketDataRepository.class);
        lock = mock(DistributedLock.class);
    }

    private RealMarketDataService service(MarketDataSource... sources) {
        return new RealMarketDataService(properties, List.of(sources), repository, lock);
    }

    @Test
    @DisplayName("主源失败 → 自动切换备用源成功（failover）")
    void failsOverToBackupSource() {
        long ts = 1791338400L;
        MarketDataSource gateio = source("gateio", false, null);
        MarketDataSource backup = source("backup", true, List.of(candle(ts, "84390")));
        when(repository.queryBars(any(), any(), any(), any(), eq(1000))).thenReturn(List.of());

        RealMarketDataService svc = service(gateio, backup);
        int inserted = svc.refresh("BTC_USDT", "1h", 200);

        assertThat(inserted).isEqualTo(1);
        Map<String, Object> status = svc.status();
        assertThat(status.get("currentSource")).isEqualTo("backup");
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> state = (Map<String, Map<String, Object>>) status.get("sourceState");
        assertThat(state.get("gateio").get("status")).isEqualTo("ERROR");
        assertThat(state.get("backup").get("status")).isEqualTo("OK");
        verify(repository).saveBar(any(BarData.class));
    }

    @Test
    @DisplayName("去重：已存在时间戳不重复写入")
    void deduplicatesExistingBars() {
        long ts = 1791338400L;
        LocalDateTime barTime = Instant.ofEpochSecond(ts)
                .atZone(ZoneId.systemDefault()).toLocalDateTime();
        BarData existing = new BarData();
        existing.setBarTime(barTime);
        when(repository.queryBars(any(), any(), any(), any(), eq(1000)))
                .thenReturn(List.of(existing));

        int inserted = service(source("gateio", true, List.of(candle(ts, "84390"))))
                .refresh("BTC_USDT", "1h", 200);

        assertThat(inserted).isZero();
    }

    @Test
    @DisplayName("全部源失败 → 抛错并携带各源错误摘要")
    void allSourcesFailThrows() {
        RealMarketDataService svc = service(
                source("gateio", false, null), source("backup", false, null));
        assertThatThrownBy(() -> svc.refresh("BTC_USDT", "1h", 200))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("真实行情刷新失败");
    }

    @Test
    @DisplayName("重试：单源在首次失败后重试成功（maxAttempts=2）")
    void retriesWithinSameSource() {
        properties.setMaxAttempts(2);
        properties.setRetryBackoffMs(1);
        long ts = 1791338400L;
        MarketDataSource flaky = new MarketDataSource() {
            private int calls = 0;

            @Override
            public String name() {
                return "gateio";
            }

            @Override
            public List<MarketCandle> fetch(String pair, String interval, int limit) {
                calls++;
                if (calls == 1) {
                    throw new IllegalStateException("瞬时故障");
                }
                return List.of(candle(ts, "84390"));
            }
        };
        when(repository.queryBars(any(), any(), any(), any(), eq(1000))).thenReturn(List.of());

        int inserted = service(flaky).refresh("BTC_USDT", "1h", 200);

        assertThat(inserted).isEqualTo(1);
        assertThat(flaky.toString()).isNotNull();
    }

    @Test
    @DisplayName("禁用时刷新被拒绝；interval<5000 定时刷新跳过")
    void disabledAndSchedulingGuard() {
        properties.setEnabled(false);
        assertThatThrownBy(() -> service(source("gateio", true, List.of(candle(1, "1"))))
                .refresh("BTC_USDT", "1h", 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未启用");

        properties.setEnabled(true);
        properties.setRefreshIntervalMs(0);
        RealMarketDataService svc = service(source("gateio", true, List.of(candle(1, "1"))));
        svc.scheduledRefresh();
        verify(repository, times(0)).saveBar(any(BarData.class));
    }
}