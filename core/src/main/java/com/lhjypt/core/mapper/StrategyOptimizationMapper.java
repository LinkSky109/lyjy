package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.backtest.StrategyOptimization;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StrategyOptimizationMapper extends BaseMapper<StrategyOptimization> {

    @Select("SELECT * FROM strategy_optimization WHERE batch_id = #{batchId} "
            + "ORDER BY symbol LIMIT 1")
    StrategyOptimization selectByBatchId(@Param("batchId") String batchId);

    @Select("SELECT * FROM strategy_optimization "
            + "ORDER BY create_time DESC, id DESC LIMIT #{limit}")
    List<StrategyOptimization> selectRecent(@Param("limit") int limit);
}