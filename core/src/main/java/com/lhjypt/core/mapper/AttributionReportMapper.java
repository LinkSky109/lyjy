package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.attribution.AttributionReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AttributionReportMapper extends BaseMapper<AttributionReport> {

    @Select("SELECT * FROM attribution_report WHERE report_id = #{reportId} AND deleted = 0")
    AttributionReport selectByReportId(@Param("reportId") String reportId);

    @Select("SELECT * FROM attribution_report WHERE deleted = 0 ORDER BY create_time DESC, id DESC LIMIT 500")
    List<AttributionReport> selectAll();
}