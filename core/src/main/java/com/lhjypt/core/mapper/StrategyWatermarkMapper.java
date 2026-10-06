package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.strategy.StrategyWatermark;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StrategyWatermarkMapper extends BaseMapper<StrategyWatermark> {

    @Select("SELECT * FROM strategy_watermark WHERE token = #{token} AND deleted = 0")
    StrategyWatermark selectByToken(@Param("token") String token);

    @Select("SELECT * FROM strategy_watermark WHERE strategy_id = #{strategyId} "
            + "AND deleted = 0 ORDER BY version DESC, id DESC LIMIT 1")
    StrategyWatermark selectLatestByStrategy(@Param("strategyId") String strategyId);
}