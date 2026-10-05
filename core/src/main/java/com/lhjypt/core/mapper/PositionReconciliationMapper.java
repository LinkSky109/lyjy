package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.trade.PositionReconciliation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PositionReconciliationMapper extends BaseMapper<PositionReconciliation> {

    @Select("SELECT * FROM position_reconciliation " +
            "ORDER BY create_time DESC LIMIT #{limit}")
    List<PositionReconciliation> selectRecent(@Param("limit") int limit);

    @Select("SELECT * FROM position_reconciliation WHERE account_id = #{accountId} " +
            "ORDER BY create_time DESC LIMIT #{limit}")
    List<PositionReconciliation> selectByAccount(@Param("accountId") String accountId,
                                                 @Param("limit") int limit);
}
