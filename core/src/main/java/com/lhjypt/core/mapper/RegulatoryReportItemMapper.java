package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.regulatory.RegulatoryReportItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RegulatoryReportItemMapper extends BaseMapper<RegulatoryReportItem> {

    @Select("SELECT * FROM regulatory_report_item WHERE batch_id = #{batchId} "
            + "ORDER BY row_no")
    List<RegulatoryReportItem> selectByBatchId(@Param("batchId") String batchId);

    @Delete("DELETE FROM regulatory_report_item WHERE batch_id = #{batchId}")
    int deleteByBatchId(@Param("batchId") String batchId);
}