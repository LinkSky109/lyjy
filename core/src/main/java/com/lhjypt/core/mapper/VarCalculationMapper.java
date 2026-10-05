package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.var.VarCalculation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface VarCalculationMapper extends BaseMapper<VarCalculation> {

    @Select("SELECT * FROM var_calculation WHERE scope_type = #{scopeType} "
            + "AND scope_id = #{scopeId} ORDER BY calc_time DESC LIMIT 1")
    VarCalculation selectLatest(@Param("scopeType") String scopeType,
                                @Param("scopeId") String scopeId);

    @Select("SELECT * FROM var_calculation WHERE scope_type = #{scopeType} "
            + "AND scope_id = #{scopeId} AND calc_time >= #{from} AND calc_time <= #{to} "
            + "ORDER BY calc_time ASC")
    List<VarCalculation> selectHistory(@Param("scopeType") String scopeType,
                                       @Param("scopeId") String scopeId,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);
}
