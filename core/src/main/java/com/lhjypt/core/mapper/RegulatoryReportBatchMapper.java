package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.regulatory.RegulatoryReportBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface RegulatoryReportBatchMapper extends BaseMapper<RegulatoryReportBatch> {

    @Select("SELECT * FROM regulatory_report_batch WHERE batch_id = #{batchId} AND deleted = 0")
    RegulatoryReportBatch selectByBatchId(@Param("batchId") String batchId);

    @Select("SELECT * FROM regulatory_report_batch WHERE report_type = #{reportType} "
            + "AND report_date = #{reportDate} AND deleted = 0 LIMIT 1")
    RegulatoryReportBatch selectByTypeAndDate(@Param("reportType") String reportType,
                                              @Param("reportDate") LocalDate reportDate);

    @Select("SELECT * FROM regulatory_report_batch WHERE deleted = 0 ORDER BY create_time DESC")
    List<RegulatoryReportBatch> selectAll();
}