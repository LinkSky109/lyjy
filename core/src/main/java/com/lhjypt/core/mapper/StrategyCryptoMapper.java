package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.strategy.StrategyCrypto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StrategyCryptoMapper extends BaseMapper<StrategyCrypto> {

    @Select("SELECT * FROM strategy_crypto WHERE strategy_id = #{strategyId} AND deleted = 0")
    StrategyCrypto selectByStrategyId(@Param("strategyId") String strategyId);

    /** 各密钥使用计数（P3-005 轮换统计）。 */
    @Select("SELECT key_id AS keyId, count(*) AS cnt FROM strategy_crypto "
            + "WHERE deleted = 0 GROUP BY key_id ORDER BY cnt DESC")
    List<java.util.Map<String, Object>> selectKeyUsage();
}
