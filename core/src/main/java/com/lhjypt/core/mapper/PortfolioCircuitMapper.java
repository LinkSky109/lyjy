package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.strategy.PortfolioCircuit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PortfolioCircuitMapper extends BaseMapper<PortfolioCircuit> {

    @Select("SELECT * FROM portfolio_circuit WHERE portfolio_key = #{portfolioKey} "
            + "AND deleted = 0")
    PortfolioCircuit selectByKey(@Param("portfolioKey") String portfolioKey);

    @Select("SELECT * FROM portfolio_circuit WHERE enabled = true AND deleted = 0")
    List<PortfolioCircuit> selectEnabled();
}