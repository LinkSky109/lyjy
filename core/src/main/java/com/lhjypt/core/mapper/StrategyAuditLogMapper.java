package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.strategy.StrategyAuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StrategyAuditLogMapper extends BaseMapper<StrategyAuditLog> {

    @Select("<script>"
            + "SELECT * FROM strategy_audit_log WHERE 1=1 "
            + "<if test='strategyId != null'> AND strategy_id = #{strategyId}</if> "
            + "<if test='action != null'> AND action = #{action}</if> "
            + "ORDER BY create_time DESC, id DESC LIMIT 500"
            + "</script>")
    List<StrategyAuditLog> selectByFilter(@Param("strategyId") String strategyId,
                                          @Param("action") String action);
}
