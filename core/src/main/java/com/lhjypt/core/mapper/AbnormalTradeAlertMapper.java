package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.surveillance.AbnormalTradeAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 异常交易告警数据访问（P1）
 */
@Mapper
public interface AbnormalTradeAlertMapper extends BaseMapper<AbnormalTradeAlert> {

    @Select("SELECT * FROM abnormal_trade_alert WHERE alert_id = #{alertId} AND deleted = 0")
    AbnormalTradeAlert selectByAlertId(@Param("alertId") String alertId);

    @Select("SELECT * FROM abnormal_trade_alert WHERE status = 'OPEN' AND deleted = 0")
    List<AbnormalTradeAlert> selectOpen();

    @Select("SELECT * FROM abnormal_trade_alert WHERE deleted = 0 " +
            "ORDER BY create_time DESC LIMIT 200")
    List<AbnormalTradeAlert> selectRecent();
}
