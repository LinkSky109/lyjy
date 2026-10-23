package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.portfolio.PortfolioNav;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface PortfolioNavMapper extends BaseMapper<PortfolioNav> {

    @Select("SELECT * FROM portfolio_nav WHERE portfolio_id = #{portfolioId} "
            + "AND nav_date BETWEEN #{from} AND #{to} ORDER BY nav_date")
    List<PortfolioNav> selectRange(@Param("portfolioId") String portfolioId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);

    @Select("SELECT * FROM portfolio_nav WHERE portfolio_id = #{portfolioId} "
            + "AND nav_date = #{navDate} LIMIT 1")
    PortfolioNav selectByDate(@Param("portfolioId") String portfolioId,
                              @Param("navDate") LocalDate navDate);
}
