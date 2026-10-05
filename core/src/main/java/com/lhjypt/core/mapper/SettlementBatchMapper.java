package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.settlement.SettlementBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface SettlementBatchMapper extends BaseMapper<SettlementBatch> {

    @Select("SELECT * FROM settlement_batch WHERE batch_id = #{batchId} LIMIT 1")
    SettlementBatch selectByBatchId(@Param("batchId") String batchId);

    @Select("SELECT * FROM settlement_batch WHERE trade_date = #{tradeDate} LIMIT 1")
    SettlementBatch selectByTradeDate(@Param("tradeDate") LocalDate tradeDate);

    @Select("SELECT * FROM settlement_batch WHERE trade_date = #{tradeDate} "
            + "ORDER BY create_time DESC")
    List<SettlementBatch> selectByDate(@Param("tradeDate") LocalDate tradeDate);
}
