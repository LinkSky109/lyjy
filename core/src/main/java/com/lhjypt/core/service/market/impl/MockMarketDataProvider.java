package com.lhjypt.core.service.market.impl;

import com.alibaba.fastjson2.JSON;
import com.lhjypt.core.model.market.TickData;
import com.lhjypt.core.service.market.MarketDataProvider;
import com.lhjypt.core.service.market.MarketDataPublisher;
import com.lhjypt.core.service.market.MarketDataRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模拟行情提供者 - P0阶段用于开发测试
 * 生成随机tick行情，模拟CTP行情推送
 * 后续可替换为真实CTP接入实现
 */
@Service
@RequiredArgsConstructor
public class MockMarketDataProvider implements MarketDataProvider {
    private static final Logger log = LoggerFactory.getLogger(MockMarketDataProvider.class);


    private final MarketDataRepository repository;
    private final MarketDataPublisher publisher;

    private final Set<String> subscribed = ConcurrentHashMap.newKeySet();
    private final Map<String, TickData> latestTicks = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> basePrices = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;
    private volatile boolean connected = false;
    private final Random random = new Random();

    /** 行情采样间隔(ms)：降级切备用时为快照级 5000，正常 500 */
    private volatile long tickIntervalMillis = 500;
    private volatile long lastGenNano = 0L;

    /**
     * 调整采样间隔（性能降级预案联动入口）
     */
    public void setTickIntervalMillis(long millis) {
        long v = Math.max(100, millis);
        this.tickIntervalMillis = v;
        log.info("Mock行情源采样间隔调整为 {}ms（降级/恢复）", v);
    }

    public long getTickIntervalMillis() {
        return tickIntervalMillis;
    }

    @PostConstruct
    public void init() {
        // 初始化默认标的基准价
        basePrices.put("rb2410", new BigDecimal("3500"));
        basePrices.put("cu2410", new BigDecimal("72000"));
        basePrices.put("IF2409", new BigDecimal("3800"));
        basePrices.put("au2410", new BigDecimal("560"));
    }

    @Override
    public void connect() {
        if (connected) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mock-market-data");
            t.setDaemon(true);
            return t;
        });
        // 每500ms生成一次tick（模拟Level-1频率）
        scheduler.scheduleAtFixedRate(this::generateTicks, 0, 500, TimeUnit.MILLISECONDS);
        connected = true;
        log.info("Mock行情源已连接，已订阅标的: {}", subscribed);
    }

    @Override
    public void disconnect() {
        connected = false;
        if (scheduler != null) {
            scheduler.shutdown();
        }
        log.info("Mock行情源已断开");
    }

    @Override
    public void subscribe(String symbol) {
        subscribed.add(symbol);
        if (!basePrices.containsKey(symbol)) {
            basePrices.put(symbol, new BigDecimal("100"));
        }
        log.info("订阅行情: {}", symbol);
    }

    @Override
    public void unsubscribe(String symbol) {
        subscribed.remove(symbol);
        latestTicks.remove(symbol);
        log.info("取消订阅: {}", symbol);
    }

    @Override
    public Set<String> getSubscribedSymbols() {
        return Collections.unmodifiableSet(subscribed);
    }

    @Override
    public TickData getLatestTick(String symbol) {
        return latestTicks.get(symbol);
    }

    @Override
    public String getName() {
        return "MOCK";
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    /**
     * 生成模拟tick数据
     */
    private void generateTicks() {
        if (!connected || subscribed.isEmpty()) return;
        long nowN = System.nanoTime();
        if (nowN - lastGenNano < tickIntervalMillis * 1_000_000L) return;
        lastGenNano = nowN;
        LocalDateTime now = LocalDateTime.now();
        for (String symbol : subscribed) {
            TickData tick = generateSingleTick(symbol, now);
            latestTicks.put(symbol, tick);
            // 异步存储 + 分发
            try {
                repository.saveTick(tick);
                publisher.publishTick(tick);
            } catch (Exception e) {
                log.error("行情存储/分发失败: {} - {}", symbol, e.getMessage());
            }
        }
    }

    private TickData generateSingleTick(String symbol, LocalDateTime now) {
        BigDecimal base = basePrices.getOrDefault(symbol, new BigDecimal("100"));
        // 价格在基准价±0.5%内随机波动
        double change = (random.nextDouble() - 0.5) * 0.01;
        BigDecimal lastPrice = base.multiply(BigDecimal.valueOf(1 + change))
                .setScale(2, RoundingMode.HALF_UP);

        TickData tick = new TickData();
        tick.setSymbol(symbol);
        tick.setLastPrice(lastPrice);
        tick.setPreClose(base);
        tick.setOpen(base.multiply(BigDecimal.valueOf(1 + (random.nextDouble() - 0.5) * 0.002)));
        tick.setHigh(lastPrice.max(tick.getOpen()));
        tick.setLow(lastPrice.min(tick.getOpen()));
        tick.setVolume(random.nextInt(1000));
        tick.setTurnover(lastPrice.multiply(BigDecimal.valueOf(tick.getVolume())));
        tick.setOpenInterest(random.nextInt(100000));

        // 五档买卖盘
        for (int i = 1; i <= 5; i++) {
            BigDecimal bidPrice = lastPrice.subtract(BigDecimal.valueOf(i * 0.2)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal askPrice = lastPrice.add(BigDecimal.valueOf(i * 0.2)).setScale(2, RoundingMode.HALF_UP);
            try {
                TickData.class.getMethod("setBidPrice" + i, BigDecimal.class).invoke(tick, bidPrice);
                TickData.class.getMethod("setAskPrice" + i, BigDecimal.class).invoke(tick, askPrice);
                TickData.class.getMethod("setBidVolume" + i, long.class).invoke(tick, random.nextInt(500));
                TickData.class.getMethod("setAskVolume" + i, long.class).invoke(tick, random.nextInt(500));
            } catch (Exception ignored) {}
        }

        tick.setTickTime(now);
        tick.setRecvTime(now);
        return tick;
    }
}
