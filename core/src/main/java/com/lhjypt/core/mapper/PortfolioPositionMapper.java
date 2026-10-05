package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.portfolio.PortfolioPosition;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PortfolioPositionMapper extends BaseMapper<PortfolioPosition> {

    @Select("SELECT * FROM portfolio_position WHERE portfolio_id = #{portfolioId} "
            + "ORDER BY symbol, direction")
    List<PortfolioPosition> selectByPortfolioId(@Param("portfolioId") String portfolioId);

    @Select("SELECT * FROM portfolio_position WHERE portfolio_id = #{portfolioId} "
            + "AND symbol = #{symbol} AND direction = #{direction} LIMIT 1")
    PortfolioPosition selectUnique(@Param("portfolioId") String portfolioId,
                                   @Param("symbol") String symbol,
                                   @Param("direction") String direction);

    @Delete("DELETE FROM portfolio_position WHERE portfolio_id = #{portfolioId}")
    int deleteByPortfolioId(@Param("portfolioId") String portfolioId);
}
