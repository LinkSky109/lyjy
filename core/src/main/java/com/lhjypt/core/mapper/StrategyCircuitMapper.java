package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.strategy.StrategyCircuit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StrategyCircuitMapper extends BaseMapper<StrategyCircuit> {

    @Select("SELECT * FROM strategy_circuit WHERE strategy_id = #{strategyId} AND deleted = 0")
    StrategyCircuit selectByStrategyId(@Param("strategyId") String strategyId);

    @Select("SELECT * FROM strategy_circuit WHERE enabled = true AND deleted = 0")
    List<StrategyCircuit> selectEnabled();
}