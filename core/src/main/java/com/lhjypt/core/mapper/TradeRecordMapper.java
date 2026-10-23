package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.trade.TradeRecord;
import com.lhjypt.core.model.trade.TurnoverAggregate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface TradeRecordMapper extends BaseMapper<TradeRecord> {

    @Select("SELECT * FROM trade_record WHERE gateway_id = #{gatewayId} "
            + "AND gateway_session_id = #{gatewaySessionId} "
            + "AND trade_id = #{tradeId} LIMIT 1")
    TradeRecord selectByTradeId(@Param("gatewayId") String gatewayId,
                                @Param("gatewaySessionId") String gatewaySessionId,
                                @Param("tradeId") String tradeId);

    @Select("SELECT * FROM trade_record WHERE order_id = #{orderId} ORDER BY trade_time")
    List<TradeRecord> selectByOrderId(@Param("orderId") String orderId);

    @Select("SELECT * FROM trade_record WHERE trade_time >= #{start} "
            + "AND trade_time < #{end} ORDER BY trade_time")
    List<TradeRecord> selectByTradeTimeRange(@Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    @Select("SELECT * FROM trade_record WHERE trading_day = #{tradingDay} ORDER BY trade_time")
    List<TradeRecord> selectByTradingDay(@Param("tradingDay") String tradingDay);

    @Select("SELECT * FROM trade_record WHERE account_id = #{accountId} "
            + "AND trade_time >= #{start} AND trade_time < #{end} ORDER BY trade_time")
    List<TradeRecord> selectByAccountAndTimeRange(@Param("accountId") String accountId,
                                                  @Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end);

    /**
     * 成交分页：accountId / symbol / 时间范围均可选。
     */
    @Select("<script>SELECT * FROM trade_record WHERE 1 = 1 "
            + "<if test='accountId != null'> AND account_id = #{accountId}</if> "
            + "<if test='symbol != null'> AND symbol = #{symbol}</if> "
            + "<if test='start != null'> AND trade_time &gt;= #{start}</if> "
            + "<if test='end != null'> AND trade_time &lt; #{end}</if> "
            + "ORDER BY trade_time DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<TradeRecord> selectPageFiltered(@Param("accountId") String accountId,
                                         @Param("symbol") String symbol,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end,
                                         @Param("offset") long offset,
                                         @Param("limit") int limit);

    /**
     * 成交分页总数。
     */
    @Select("<script>SELECT COUNT(*) FROM trade_record WHERE 1 = 1 "
            + "<if test='accountId != null'> AND account_id = #{accountId}</if> "
            + "<if test='symbol != null'> AND symbol = #{symbol}</if> "
            + "<if test='start != null'> AND trade_time &gt;= #{start}</if> "
            + "<if test='end != null'> AND trade_time &lt; #{end}</if></script>")
    long countPageFiltered(@Param("accountId") String accountId,
                           @Param("symbol") String symbol,
                           @Param("start") LocalDateTime start,
                           @Param("end") LocalDateTime end);

    @Select("SELECT * FROM trade_record WHERE turnover >= #{minTurnover} "
            + "AND trade_time >= #{start} ORDER BY trade_time")
    List<TradeRecord> selectLargeTrades(@Param("minTurnover") BigDecimal minTurnover,
                                        @Param("start") LocalDateTime start);

    @Select("SELECT account_id, SUM(turnover) AS turnover FROM trade_record "
            + "WHERE trade_time >= #{since} GROUP BY account_id "
            + "HAVING SUM(turnover) >= #{minTurnover}")
    List<TurnoverAggregate> selectCumulativeTurnover(
            @Param("since") LocalDateTime since,
            @Param("minTurnover") BigDecimal minTurnover);
}
