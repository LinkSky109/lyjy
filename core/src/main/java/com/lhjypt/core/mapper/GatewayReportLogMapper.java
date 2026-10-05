package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.trade.GatewayReportLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GatewayReportLogMapper extends BaseMapper<GatewayReportLog> {

    @Select("SELECT * FROM gateway_report_log WHERE gateway_id = #{gatewayId} " +
            "AND report_type = #{reportType} AND report_key = #{reportKey} LIMIT 1")
    GatewayReportLog selectByReportKey(@Param("gatewayId") String gatewayId,
                                       @Param("reportType") String reportType,
                                       @Param("reportKey") String reportKey);

    @Select("SELECT * FROM gateway_report_log WHERE process_status IN ('RECEIVED', 'FAILED') " +
            "ORDER BY create_time LIMIT #{limit}")
    List<GatewayReportLog> selectPending(@Param("limit") int limit);
}
