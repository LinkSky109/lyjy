package com.lhjypt.core.model.portfolio;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RebalanceInstruction {

    public static final String PENDING = "PENDING";
    public static final String REJECTED = "REJECTED";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    private int index;

    private String symbol;

    private String exchange;

    private String direction;

    private String offsetFlag;

    private BigDecimal quantity;

    private BigDecimal price;

    private BigDecimal estimatedCost;

    private String status;

    private String reason;

    private String orderId;
}
