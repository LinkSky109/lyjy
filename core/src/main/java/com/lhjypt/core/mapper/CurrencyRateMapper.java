package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.settlement.CurrencyRate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface CurrencyRateMapper extends BaseMapper<CurrencyRate> {

    @Select("SELECT * FROM currency_rate WHERE base = #{base} AND quote = #{quote} "
            + "AND rate_date <= #{date} ORDER BY rate_date DESC LIMIT 1")
    CurrencyRate selectLatest(@Param("base") String base, @Param("quote") String quote,
                              @Param("date") LocalDate date);

    @Select("SELECT * FROM currency_rate WHERE base = #{base} AND quote = #{quote} "
            + "AND rate_date = #{date} LIMIT 1")
    CurrencyRate selectByPair(@Param("base") String base, @Param("quote") String quote,
                              @Param("date") LocalDate date);

    @Select("SELECT * FROM currency_rate ORDER BY base, quote, rate_date DESC LIMIT 500")
    List<CurrencyRate> selectAll();
}