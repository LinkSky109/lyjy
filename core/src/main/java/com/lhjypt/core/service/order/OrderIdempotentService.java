package com.lhjypt.core.service.order;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lhjypt.core.mapper.IdempotentMapper;
import com.lhjypt.core.model.order.IdempotentRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 订单幂等性服务
 *
 * 保证下单请求幂等：
 * 1. 客户端为每次下单生成唯一幂等键
 * 2. 首次请求：记录幂等键，正常处理
 * 3. 重复请求（网络重试）：检测到幂等键已存在，直接返回原订单，不重复下单
 * 4. 同一幂等键但请求内容不同：拒绝，防止键复用
 */
@Service
@RequiredArgsConstructor
public class OrderIdempotentService {
    private static final Logger log = LoggerFactory.getLogger(OrderIdempotentService.class);


    private final IdempotentMapper idempotentMapper;

    /**
     * 幂等检查结果
     */
    public record IdempotentCheck(boolean isDuplicate, String existingOrderId, String requestHash) {}

    /**
     * 检查请求是否重复
     */
    public IdempotentCheck check(String idempotentKey, String requestContent) {
        if (idempotentKey == null || idempotentKey.isBlank()) {
            throw new IllegalArgumentException("幂等键不能为空");
        }

        String requestHash = sha256(requestContent);
        IdempotentRecord existing = idempotentMapper.selectByKey(idempotentKey);

        if (existing == null) {
            // 首次请求，创建幂等记录
            IdempotentRecord record = new IdempotentRecord();
            record.setIdempotentKey(idempotentKey);
            record.setRequestHash(requestHash);
            record.setStatus("PROCESSING");
            record.setCreateTime(LocalDateTime.now());
            idempotentMapper.insert(record);
            return new IdempotentCheck(false, null, requestHash);
        }

        // 重复请求，校验请求内容是否一致
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IllegalStateException(
                    "幂等键 " + idempotentKey + " 已被不同请求使用，禁止复用");
        }

        log.info("重复下单请求，幂等键: {}，返回原订单: {}", idempotentKey, existing.getOrderId());
        return new IdempotentCheck(true, existing.getOrderId(), requestHash);
    }

    /**
     * 标记幂等请求完成
     */
    public void complete(String idempotentKey, String orderId) {
        IdempotentRecord record = idempotentMapper.selectByKey(idempotentKey);
        if (record != null) {
            record.setStatus("COMPLETED");
            record.setOrderId(orderId);
            idempotentMapper.updateById(record);
        }
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256计算失败", e);
        }
    }
}
