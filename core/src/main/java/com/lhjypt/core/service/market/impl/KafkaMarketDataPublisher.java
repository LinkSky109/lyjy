package com.lhjypt.core.service.market.impl;

import com.alibaba.fastjson2.JSON;
import com.lhjypt.core.model.market.TickData;
import com.lhjypt.core.service.market.MarketDataPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka行情分发实现
 * 将tick行情发布到Kafka，供策略消费
 */
@Service
@RequiredArgsConstructor
public class KafkaMarketDataPublisher implements MarketDataPublisher {
    private static final Logger log = LoggerFactory.getLogger(KafkaMarketDataPublisher.class);


    private final KafkaTemplate<String, String> kafkaTemplate;

    public static final String TICK_TOPIC = "lhjypt-market-tick";

    @Override
    public void publishTick(TickData tick) {
        try {
            String json = JSON.toJSONString(tick);
            kafkaTemplate.send(TICK_TOPIC, tick.getSymbol(), json);
        } catch (Exception e) {
            log.warn("Kafka发布行情失败（可能服务未启动）: {} - {}", tick.getSymbol(), e.getMessage());
        }
    }

    @Override
    public String getTickTopic() {
        return TICK_TOPIC;
    }
}
