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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    // ---------- P4-001 分页回补 ----------

    /** 支持分页的假源：按结束游标返回不同页（cursor=null 为最近一页）。 */
    private static MarketDataSource pagingSource(String name,
            java.util.function.Function<Long, List<MarketCandle>> pageFn) {
        return new MarketDataSource() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public List<MarketCandle> fetch(String pair, String interval, int limit) {
                return pageFn.apply(null);
            }

            @Override
            public boolean supportsBackfill() {
                return true;
            }

            @Override
            public List<MarketCandle> fetch(String pair, String interval, int limit, Long cursor) {
                return pageFn.apply(cursor);
            }
        };
    }

    @Test
    @DisplayName("分页回补：逐页向前翻直到不足一页，累计去重写入")
    void backfillPaginatesUntilShortPage() {
        properties.setBackfillMaxPages(10);
        MarketDataSource gateio = pagingSource("gateio", cursor -> {
            if (cursor == null) {
                return List.of(candle(600, "6"), candle(500, "5"));
            }
            if (cursor == 499L) {
                return List.of(candle(400, "4"), candle(300, "3"));
            }
            if (cursor == 299L) {
                return List.of(candle(200, "2"), candle(100, "1"));
            }
            if (cursor == 99L) {
                return List.of(candle(50, "0.5"));
            }
            return List.of();
        });

        RealMarketDataService.BackfillResult result =
                service(gateio).backfill("BTC_USDT", "1h", 100, 2, null);

        assertThat(result.pages()).isEqualTo(4);
        assertThat(result.fetched()).isEqualTo(7);
        assertThat(result.inserted()).isEqualTo(7);
        assertThat(result.source()).isEqualTo("gateio");
        verify(repository, times(7)).saveBar(any(BarData.class));
    }

    @Test
    @DisplayName("分页回补：达到目标条数即停止")
    void backfillStopsAtTarget() {
        properties.setBackfillMaxPages(10);
        MarketDataSource gateio = pagingSource("gateio", cursor -> cursor == null
                ? List.of(candle(600, "6"), candle(500, "5"))
                : List.of(candle(400, "4"), candle(300, "3")));

        RealMarketDataService.BackfillResult result =
                service(gateio).backfill("BTC_USDT", "1h", 3, 2, null);

        assertThat(result.pages()).isEqualTo(2);
        assertThat(result.fetched()).isEqualTo(4);
    }

    @Test
    @DisplayName("分页回补：源忽略游标致本页无新增时停止（防死循环）")
    void backfillStopsOnNoProgress() {
        properties.setBackfillMaxPages(10);
        MarketDataSource stuck = pagingSource("gateio",
                cursor -> List.of(candle(300, "3"), candle(200, "2")));

        RealMarketDataService.BackfillResult result =
                service(stuck).backfill("BTC_USDT", "1h", 100, 2, null);

        assertThat(result.pages()).isEqualTo(2);
        assertThat(result.fetched()).isEqualTo(2);
        verify(repository, times(2)).saveBar(any(BarData.class));
    }

    @Test
    @DisplayName("分页回补：已存在时间戳不重复写入")
    void backfillDedupsExisting() {
        properties.setBackfillMaxPages(10);
        LocalDateTime existingTime = Instant.ofEpochSecond(200)
                .atZone(ZoneId.systemDefault()).toLocalDateTime();
        BarData existing = new BarData();
        existing.setBarTime(existingTime);
        when(repository.queryBars(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(existing));

        MarketDataSource gateio = pagingSource("gateio",
                cursor -> List.of(candle(200, "2"), candle(100, "1")));

        RealMarketDataService.BackfillResult result =
                service(gateio).backfill("BTC_USDT", "1h", 2, 2, null);

        assertThat(result.fetched()).isEqualTo(2);
        assertThat(result.inserted()).isEqualTo(1);
        verify(repository, times(1)).saveBar(any(BarData.class));
    }

    @Test
    @DisplayName("分页回补：指定源只用该源，不触发故障切换")
    void backfillUsesForcedSource() throws Exception {
        properties.setBackfillMaxPages(10);
        MarketDataSource gateio = mock(MarketDataSource.class);
        when(gateio.name()).thenReturn("gateio");
        MarketDataSource backup = pagingSource("backup", cursor -> List.of(candle(200, "2")));

        RealMarketDataService.BackfillResult result =
                service(gateio, backup).backfill("BTC_USDT", "1h", 100, 1000, "backup");

        assertThat(result.source()).isEqualTo("backup");
        assertThat(result.fetched()).isEqualTo(1);
        assertThat(result.pages()).isEqualTo(1);
        verify(gateio, never()).fetch(anyString(), anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("分页回补：源不支持分页时退化为单页")
    void backfillNonPagingSourceSinglePage() {
        properties.setBackfillMaxPages(10);
        MarketDataSource plain = source("gateio", true,
                List.of(candle(200, "2"), candle(100, "1")));

        RealMarketDataService.BackfillResult result =
                service(plain).backfill("BTC_USDT", "1h", 100, 2, null);

        assertThat(result.pages()).isEqualTo(1);
        assertThat(result.fetched()).isEqualTo(2);
        assertThat(result.inserted()).isEqualTo(2);
    }

    @Test
    @DisplayName("分页回补：全部源失败抛错")
    void backfillAllSourcesFail() {
        RealMarketDataService svc = service(
                source("gateio", false, null), source("backup", false, null));
        assertThatThrownBy(() -> svc.backfill("BTC_USDT", "1h", 100, 2, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("行情分页回补失败");
    }
}