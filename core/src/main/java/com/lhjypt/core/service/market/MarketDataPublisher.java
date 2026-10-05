package com.lhjypt.core.service.market;

import com.lhjypt.core.model.market.TickData;

/**
 * 行情分发接口 - Kafka事件驱动
 */
public interface MarketDataPublisher {

    /**
     * 发布tick行情到Kafka
     */
    void publishTick(TickData tick);

    /**
     * 行情Topic名称
     */
    String getTickTopic();
}
