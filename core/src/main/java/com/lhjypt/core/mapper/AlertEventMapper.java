package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.monitor.AlertEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 告警事件数据访问层
 */
@Mapper
public interface AlertEventMapper extends BaseMapper<AlertEvent> {

    /**
     * 根据事件ID查询
     */
    @Select("SELECT * FROM alert_event WHERE event_id = #{eventId}")
    AlertEvent selectByEventId(@Param("eventId") String eventId);

    /**
     * 按状态查询告警事件
     */
    @Select("SELECT * FROM alert_event WHERE status = #{status} ORDER BY create_time DESC LIMIT 100")
    List<AlertEvent> selectByStatus(@Param("status") String status);

    /**
     * 查询全部告警事件（最近200条）
     */
    @Select("SELECT * FROM alert_event ORDER BY create_time DESC LIMIT 200")
    List<AlertEvent> selectRecent();

    /** 按规则 ID 查询最近告警（策略/组合风控看板）。 */
    @Select("<script>SELECT * FROM alert_event WHERE rule_id IN "
            + "<foreach collection='ruleIds' item='rid' open='(' separator=',' close=')'>#{rid}</foreach> "
            + "ORDER BY create_time DESC LIMIT #{limit}</script>")
    List<AlertEvent> selectByRuleIds(@Param("ruleIds") List<String> ruleIds,
                                     @Param("limit") int limit);

    /**
     * 确认告警
     */
    @Update("UPDATE alert_event SET status = 'ACKED', ack_by = #{ackBy}, ack_time = now() " +
            "WHERE event_id = #{eventId} AND status = 'OPEN'")
    int ackEvent(@Param("eventId") String eventId, @Param("ackBy") String ackBy);

    /**
     * 关闭告警
     */
    @Update("UPDATE alert_event SET status = 'CLOSED' WHERE event_id = #{eventId}")
    int closeEvent(@Param("eventId") String eventId);
}
