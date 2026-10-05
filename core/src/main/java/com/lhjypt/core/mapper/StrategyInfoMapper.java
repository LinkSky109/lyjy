package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.strategy.StrategyInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StrategyInfoMapper extends BaseMapper<StrategyInfo> {

    @Select("SELECT * FROM strategy_info WHERE strategy_id = #{strategyId} AND deleted = 0")
    StrategyInfo selectByStrategyId(@Param("strategyId") String strategyId);

    @Select("SELECT * FROM strategy_info WHERE owner = #{owner} AND deleted = 0 "
            + "ORDER BY create_time DESC")
    List<StrategyInfo> selectByOwner(@Param("owner") String owner);
}
