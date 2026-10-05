package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.strategy.StrategyCrypto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StrategyCryptoMapper extends BaseMapper<StrategyCrypto> {

    @Select("SELECT * FROM strategy_crypto WHERE strategy_id = #{strategyId} AND deleted = 0")
    StrategyCrypto selectByStrategyId(@Param("strategyId") String strategyId);
}
