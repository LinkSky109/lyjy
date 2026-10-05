package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.fund.OrderFundReservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderFundReservationMapper extends BaseMapper<OrderFundReservation> {

    @Select("SELECT * FROM order_fund_reservation WHERE order_id = #{orderId} LIMIT 1")
    OrderFundReservation selectByOrderId(@Param("orderId") String orderId);

    @Select("SELECT * FROM order_fund_reservation WHERE account_id = #{accountId} " +
            "AND status IN ('RESERVED', 'PARTIAL_CONSUMED') ORDER BY create_time")
    List<OrderFundReservation> selectActiveByAccount(@Param("accountId") String accountId);
}
