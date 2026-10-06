package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.model.settlement.CurrencyRate;
import com.lhjypt.core.service.settlement.FxService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 多币种汇率与折算 API（P2 模块一 §1.1）。
 */
@RestController
@RequestMapping("/api/currency")
@RequiredArgsConstructor
public class CurrencyController {

    private final FxService fxService;

    @GetMapping("/rates")
    public Result<List<CurrencyRate>> rates() {
        return Result.success(fxService.rates());
    }

    @GetMapping("/convert")
    public Result<Map<String, Object>> convert(
            @RequestParam BigDecimal amount,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(Map.of(
                "amount", amount,
                "from", from,
                "to", to,
                "converted", fxService.convert(amount, from, to, date)));
    }

    @PostMapping("/rate")
    public Result<CurrencyRate> upsert(@RequestBody CurrencyRate rate) {
        return Result.success(fxService.upsert(rate));
    }
}