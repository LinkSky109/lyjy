package com.lhjypt.core.service.settlement;

import com.lhjypt.core.mapper.CurrencyRateMapper;
import com.lhjypt.core.model.settlement.CurrencyRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FxServiceTest {

    private CurrencyRateMapper rateMapper;
    private FxService service;
    private final LocalDate day = LocalDate.of(2026, 10, 6);

    @BeforeEach
    void setUp() {
        rateMapper = mock(CurrencyRateMapper.class);
        service = new FxService(rateMapper);
    }

    @Test
    @DisplayName("同币种恒等")
    void sameCurrencyIdentity() {
        assertThat(service.convert(new BigDecimal("100"), "CNY", "cny", day))
                .isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("直接汇率换算")
    void directRate() {
        rate("CNY", "USD", "0.14", day);
        BigDecimal result = service.convert(new BigDecimal("100"), "CNY", "USD", day);
        assertThat(result).isEqualByComparingTo(new BigDecimal("14.00000000"));
    }

    @Test
    @DisplayName("无直接汇率时用反向汇率折算")
    void inverseRate() {
        when(rateMapper.selectLatest("CNY", "HKD", day)).thenReturn(null);
        rate("HKD", "CNY", "0.93", day);
        BigDecimal result = service.convert(new BigDecimal("100"), "CNY", "HKD", day);
        assertThat(result.doubleValue()).isCloseTo(107.52688172, within(0.0001));
    }

    @Test
    @DisplayName("缺汇率报错")
    void missingRateThrows() {
        assertThatThrownBy(() -> service.convert(new BigDecimal("1"), "CNY", "JPY", day))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少汇率");
    }

    @Test
    @DisplayName("汇率 upsert：新增插、存在更新")
    void upsert() {
        CurrencyRate rate = new CurrencyRate();
        rate.setBase("CNY");
        rate.setQuote("EUR");
        rate.setRate(new BigDecimal("0.13"));
        rate.setRateDate(day);

        when(rateMapper.selectByPair("CNY", "EUR", day)).thenReturn(null);
        CurrencyRate inserted = service.upsert(rate);
        verify(rateMapper).insert(any(CurrencyRate.class));

        CurrencyRate existing = new CurrencyRate();
        existing.setId(1L);
        existing.setBase("CNY");
        existing.setQuote("EUR");
        existing.setRate(new BigDecimal("0.12"));
        existing.setRateDate(day);
        when(rateMapper.selectByPair("CNY", "EUR", day)).thenReturn(existing);
        CurrencyRate updated = service.upsert(rate);
        assertThat(updated.getRate()).isEqualByComparingTo(new BigDecimal("0.13"));
        assertThat(updated.getId()).isEqualTo(1L);
        verify(rateMapper).updateById(existing);
    }

    private void rate(String base, String quote, String rate, LocalDate date) {
        CurrencyRate value = new CurrencyRate();
        value.setBase(base);
        value.setQuote(quote);
        value.setRate(new BigDecimal(rate));
        value.setRateDate(date);
        when(rateMapper.selectLatest(base, quote, date)).thenReturn(value);
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}