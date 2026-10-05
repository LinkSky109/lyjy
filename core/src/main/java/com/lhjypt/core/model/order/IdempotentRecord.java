package com.lhjypt.core.model.order;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 幂等键记录
 * 用于保证下单请求的幂等性，防止网络重试导致重复下单
 */
@TableName("order_idempotent")
public class IdempotentRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户端幂等键（请求方生成，全局唯一） */
    private String idempotentKey;

    /** 生成的订单ID */
    private String orderId;

    /** 请求摘要（用于校验同一幂等键的请求是否一致） */
    private String requestHash;

    /** 状态：PROCESSING(处理中)/COMPLETED(已完成) */
    private String status;

    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public String getIdempotentKey() {
        return idempotentKey;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setIdempotentKey(String idempotentKey) {
        this.idempotentKey = idempotentKey;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "IdempotentRecord{id=" + id + ", idempotentKey=" + idempotentKey + ", orderId=" + orderId + ", requestHash=" + requestHash + ", status=" + status + ", createTime=" + createTime + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdempotentRecord that = (IdempotentRecord) o;
        return Objects.equals(id, that.id)
                && Objects.equals(idempotentKey, that.idempotentKey)
                && Objects.equals(orderId, that.orderId)
                && Objects.equals(requestHash, that.requestHash)
                && Objects.equals(status, that.status)
                && Objects.equals(createTime, that.createTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, idempotentKey, orderId, requestHash, status, createTime);
    }
}
