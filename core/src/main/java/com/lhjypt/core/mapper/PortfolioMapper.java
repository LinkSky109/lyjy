package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.portfolio.Portfolio;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PortfolioMapper extends BaseMapper<Portfolio> {

    @Select("SELECT * FROM portfolio WHERE portfolio_id = #{portfolioId} AND deleted = 0")
    Portfolio selectByPortfolioId(@Param("portfolioId") String portfolioId);

    @Select("SELECT * FROM portfolio WHERE owner = #{owner} AND deleted = 0 "
            + "ORDER BY create_time DESC")
    List<Portfolio> selectByOwner(@Param("owner") String owner);

    @Select("SELECT COUNT(*) FROM portfolio WHERE tenant_id = #{tenantId} AND deleted = 0")
    int countByTenant(@Param("tenantId") String tenantId);
}
