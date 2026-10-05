package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.trade.TradeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TradeRecordMapper extends BaseMapper<TradeRecord> {

    @Select("SELECT * FROM trade_record WHERE trade_id = #{tradeId} LIMIT 1")
    TradeRecord selectByTradeId(@Param("tradeId") String tradeId);

    @Select("SELECT * FROM trade_record WHERE order_id = #{orderId} ORDER BY trade_time")
    List<TradeRecord> selectByOrderId(@Param("orderId") String orderId);
}
