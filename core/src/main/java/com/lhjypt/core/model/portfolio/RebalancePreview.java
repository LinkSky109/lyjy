package com.lhjypt.core.model.portfolio;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class RebalancePreview {

    private List<RebalanceInstruction> instructions = new ArrayList<>();

    private BigDecimal diffTotal = BigDecimal.ZERO;

    private BigDecimal costEst = BigDecimal.ZERO;
}
