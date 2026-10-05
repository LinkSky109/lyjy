package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.Position;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PositionMapper extends BaseMapper<Position> {

    @Select("SELECT * FROM position WHERE account_id = #{accountId} AND deleted = 0 " +
            "ORDER BY symbol, direction")
    List<Position> selectByAccount(@Param("accountId") String accountId);

    @Select("SELECT * FROM position WHERE account_id = #{accountId} " +
            "AND strategy_id = #{strategyId} AND deleted = 0 ORDER BY symbol, direction")
    List<Position> selectByAccountAndStrategy(@Param("accountId") String accountId,
                                              @Param("strategyId") String strategyId);

    @Select("SELECT * FROM position WHERE strategy_id = #{strategyId} AND deleted = 0 " +
            "ORDER BY account_id, symbol, direction")
    List<Position> selectByStrategy(@Param("strategyId") String strategyId);

    @Select("SELECT * FROM position WHERE account_id = #{accountId} " +
            "AND strategy_id = #{strategyId} AND symbol = #{symbol} " +
            "AND direction = #{direction} AND deleted = 0 LIMIT 1")
    Position selectUnique(@Param("accountId") String accountId,
                          @Param("strategyId") String strategyId,
                          @Param("symbol") String symbol,
                          @Param("direction") String direction);
}
