package com.lhjypt.core.service.settlement;

import com.lhjypt.core.mapper.CurrencyRateMapper;
import com.lhjypt.core.model.settlement.CurrencyRate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 多币种汇率与折算（P2 模块一 §1.1）。
 * 折算取最近可用汇率；无直接汇率则用反向汇率换算；同币种恒等。
 */
@Service
@RequiredArgsConstructor
public class FxService {

    private final CurrencyRateMapper rateMapper;

    public BigDecimal convert(BigDecimal amount, String from, String to, LocalDate date) {
        if (amount == null || amount.signum() == 0) {
            return BigDecimal.ZERO;
        }
        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            throw new IllegalArgumentException("币种不能为空");
        }
        String base = from.trim().toUpperCase();
        String quote = to.trim().toUpperCase();
        if (base.equals(quote)) {
            return amount;
        }
        LocalDate effective = date == null ? LocalDate.now() : date;
        CurrencyRate direct = rateMapper.selectLatest(base, quote, effective);
        if (direct != null) {
            return amount.multiply(direct.getRate()).setScale(8, RoundingMode.HALF_UP);
        }
        CurrencyRate inverse = rateMapper.selectLatest(quote, base, effective);
        if (inverse != null && inverse.getRate().signum() != 0) {
            return amount.divide(inverse.getRate(), 8, RoundingMode.HALF_UP);
        }
        throw new IllegalStateException("缺少汇率: " + base + "/" + quote);
    }

    public List<CurrencyRate> rates() {
        return rateMapper.selectAll();
    }

    @Transactional
    public CurrencyRate upsert(CurrencyRate rate) {
        if (rate == null || rate.getBase() == null || rate.getQuote() == null
                || rate.getRate() == null || rate.getRateDate() == null) {
            throw new IllegalArgumentException("base/quote/rate/rateDate 不能为空");
        }
        rate.setBase(rate.getBase().trim().toUpperCase());
        rate.setQuote(rate.getQuote().trim().toUpperCase());
        CurrencyRate existing = rateMapper.selectByPair(
                rate.getBase(), rate.getQuote(), rate.getRateDate());
        if (existing == null) {
            rate.setCreateTime(LocalDateTime.now());
            rate.setUpdateTime(LocalDateTime.now());
            rateMapper.insert(rate);
            return rate;
        }
        existing.setRate(rate.getRate());
        existing.setUpdateTime(LocalDateTime.now());
        rateMapper.updateById(existing);
        return existing;
    }
}