package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.settlement.SettlementDiffItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SettlementDiffItemMapper extends BaseMapper<SettlementDiffItem> {

    @Select("SELECT * FROM settlement_diff_item WHERE diff_id = #{diffId} LIMIT 1")
    SettlementDiffItem selectByDiffId(@Param("diffId") String diffId);

    @Select("SELECT * FROM settlement_diff_item WHERE batch_id = #{batchId} "
            + "ORDER BY id")
    List<SettlementDiffItem> selectByBatchId(@Param("batchId") String batchId);
}
