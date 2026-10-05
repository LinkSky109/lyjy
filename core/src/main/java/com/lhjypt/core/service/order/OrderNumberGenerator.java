package com.lhjypt.core.service.order;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 订单编号生成器
 * 生成全局唯一订单号
 * 格式：LHJ + yyyyMMddHHmmss + 6位序列号
 * 如：LHJ20261002140530000001
 */
@Component
public class OrderNumberGenerator {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AtomicInteger sequence = new AtomicInteger(0);
    private volatile String lastTimePart = "";

    /**
     * 生成订单号
     */
    public synchronized String generate() {
        String timePart = LocalDateTime.now().format(FORMATTER);

        // 同一秒内递增，跨秒重置
        if (!timePart.equals(lastTimePart)) {
            lastTimePart = timePart;
            sequence.set(0);
        }

        int seq = sequence.incrementAndGet();
        return "LHJ" + timePart + String.format("%06d", seq);
    }
}
