package com.lhjypt.core.service.order;

/**
 * 订单状态枚举 - OMS订单全生命周期
 */
public enum OrderStatus {

    /** 待提交（本地创建，未发往柜台） */
    PENDING("PENDING", "待提交"),

    /** 已提交（已发往柜台，等待确认） */
    SUBMITTED("SUBMITTED", "已提交"),

    /** 柜台已接受 */
    ACCEPTED("ACCEPTED", "已接受"),

    /** 部分成交 */
    PARTIAL_FILLED("PARTIAL_FILLED", "部分成交"),

    /** 全部成交（终态） */
    FILLED("FILLED", "全部成交"),

    /** 已撤销（终态） */
    CANCELED("CANCELED", "已撤销"),

    /** 已拒绝（终态） */
    REJECTED("REJECTED", "已拒绝"),

    /** 提交超时（终态） */
    TIMEOUT("TIMEOUT", "提交超时");

    private final String code;
    private final String description;

    OrderStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() { return code; }
    public String getDescription() { return description; }

    /**
     * 是否终态（不可再流转）
     */
    public boolean isTerminal() {
        return this == FILLED || this == CANCELED || this == REJECTED || this == TIMEOUT;
    }

    /**
     * 是否活跃状态（可能成交）
     */
    public boolean isActive() {
        return this == SUBMITTED || this == ACCEPTED || this == PARTIAL_FILLED;
    }

    public static OrderStatus fromCode(String code) {
        for (OrderStatus s : values()) {
            if (s.code.equals(code)) return s;
        }
        throw new IllegalArgumentException("未知订单状态: " + code);
    }
}
