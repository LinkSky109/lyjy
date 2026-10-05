package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.risk.RiskDispositionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 风控处置记录数据访问层（P1）
 */
@Mapper
public interface RiskDispositionLogMapper extends BaseMapper<RiskDispositionLog> {

    @Select("SELECT * FROM risk_disposition_log WHERE disposition_id = #{id} AND deleted = 0")
    RiskDispositionLog selectByDispositionId(@Param("id") String dispositionId);

    @Select("SELECT * FROM risk_disposition_log WHERE deleted = 0 ORDER BY create_time DESC LIMIT 200")
    List<RiskDispositionLog> selectRecent();

    @Select("SELECT * FROM risk_disposition_log WHERE result = 'PENDING' AND deleted = 0 ORDER BY create_time DESC")
    List<RiskDispositionLog> selectPending();
}