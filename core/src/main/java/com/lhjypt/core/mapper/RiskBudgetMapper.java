package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.risk.RiskBudget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RiskBudgetMapper extends BaseMapper<RiskBudget> {

    @Select("SELECT * FROM risk_budget WHERE budget_id = #{budgetId} AND deleted = 0")
    RiskBudget selectByBudgetId(@Param("budgetId") String budgetId);

    @Select("SELECT * FROM risk_budget WHERE scope_type = #{scopeType} "
            + "AND scope_id = #{scopeId} AND budget_type = #{budgetType} "
            + "AND deleted = 0 LIMIT 1")
    RiskBudget selectByScope(@Param("scopeType") String scopeType,
                             @Param("scopeId") String scopeId,
                             @Param("budgetType") String budgetType);

    @Select("<script>"
            + "SELECT * FROM risk_budget WHERE deleted = 0 AND enabled = TRUE "
            + "<if test='scopeType != null'> AND scope_type = #{scopeType}</if> "
            + "<if test='scopeId != null'> AND scope_id = #{scopeId}</if> "
            + "ORDER BY scope_type, scope_id"
            + "</script>")
    List<RiskBudget> selectFiltered(@Param("scopeType") String scopeType,
                                    @Param("scopeId") String scopeId);

    @Select("<script>"
            + "SELECT * FROM risk_budget WHERE deleted = 0 "
            + "<if test='scopeType != null'> AND scope_type = #{scopeType}</if> "
            + "<if test='scopeId != null'> AND scope_id = #{scopeId}</if> "
            + "ORDER BY scope_type, scope_id"
            + "</script>")
    List<RiskBudget> selectAllFiltered(@Param("scopeType") String scopeType,
                                       @Param("scopeId") String scopeId);

    @Select("SELECT * FROM risk_budget WHERE deleted = 0 AND enabled = TRUE")
    List<RiskBudget> selectEnabled();
}