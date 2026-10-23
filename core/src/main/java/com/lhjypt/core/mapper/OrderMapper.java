package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单数据访问层
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 根据策略查询订单
     */
    @Select("SELECT * FROM orders WHERE strategy_id = #{strategyId} " +
            "AND deleted = 0 ORDER BY create_time DESC")
    List<Order> selectByStrategy(@Param("strategyId") String strategyId);

    /**
     * 根据状态查询活跃订单
     */
    @Select("SELECT * FROM orders WHERE status IN "
            + "('PENDING','SUBMITTED','ACCEPTED','PARTIAL_FILLED','SUBMIT_UNKNOWN') " +
            "AND deleted = 0")
    List<Order> selectActiveOrders();

    /**
     * 根据订单号查询
     */
    @Select("SELECT * FROM orders WHERE order_id = #{orderId} AND deleted = 0")
    Order selectByOrderId(@Param("orderId") String orderId);

    /**
     * 根据柜台订单引用查询
     */
    @Select("SELECT * FROM orders WHERE broker_order_ref = #{brokerOrderRef} AND deleted = 0 LIMIT 1")
    Order selectByBrokerOrderRef(@Param("brokerOrderRef") String brokerOrderRef);

    /**
     * 分页查询订单（OMS 历史列表）。
     */
    @Select("<script>SELECT * FROM orders WHERE deleted = 0 "
            + "<if test='status != null'> AND status = #{status}</if> "
            + "<if test='symbol != null'> AND symbol = #{symbol}</if> "
            + "ORDER BY create_time DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Order> selectPageFiltered(@Param("status") String status,
                                   @Param("symbol") String symbol,
                                   @Param("offset") long offset,
                                   @Param("limit") int limit);

    /**
     * 分页查询订单总数。
     */
    @Select("<script>SELECT COUNT(*) FROM orders WHERE deleted = 0 "
            + "<if test='status != null'> AND status = #{status}</if> "
            + "<if test='symbol != null'> AND symbol = #{symbol}</if></script>")
    long countFiltered(@Param("status") String status,
                       @Param("symbol") String symbol);

    /**
     * 滑动窗口内的订单（异常交易监控评分取数，P1）
     */
    @Select("SELECT * FROM orders WHERE create_time >= #{since} AND deleted = 0 " +
            "ORDER BY create_time")
    List<Order> selectCreatedSince(@Param("since") LocalDateTime since);
}
