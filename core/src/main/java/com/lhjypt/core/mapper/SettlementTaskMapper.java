package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.settlement.SettlementTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface SettlementTaskMapper extends BaseMapper<SettlementTask> {

    @Select("SELECT * FROM settlement_task WHERE task_id = #{taskId} AND deleted = 0")
    SettlementTask selectByTaskId(@Param("taskId") String taskId);

    @Select("SELECT * FROM settlement_task WHERE trade_id = #{tradeId} AND deleted = 0 LIMIT 1")
    SettlementTask selectByTradeId(@Param("tradeId") String tradeId);

    @Select("<script>"
            + "SELECT * FROM settlement_task WHERE deleted = 0 "
            + "<if test='status != null'> AND status = #{status}</if> "
            + "ORDER BY expected_date, id DESC LIMIT 500"
            + "</script>")
    List<SettlementTask> selectByStatus(@Param("status") String status);

    @Select("SELECT * FROM settlement_task WHERE deleted = 0 AND status = 'PENDING' "
            + "AND expected_date <= #{date} ORDER BY expected_date")
    List<SettlementTask> selectPendingDue(@Param("date") LocalDate date);
}