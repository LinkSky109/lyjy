package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.monitor.AlertRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 告警规则数据访问层
 */
@Mapper
public interface AlertRuleMapper extends BaseMapper<AlertRule> {

    /**
     * 根据规则ID查询
     */
    @Select("SELECT * FROM alert_rule WHERE rule_id = #{ruleId} AND deleted = 0")
    AlertRule selectByRuleId(@Param("ruleId") String ruleId);

    /**
     * 查询所有启用的规则
     */
    @Select("SELECT * FROM alert_rule WHERE enabled = true AND deleted = 0")
    List<AlertRule> selectEnabledRules();

    /**
     * 切换规则启用状态
     */
    @Update("UPDATE alert_rule SET enabled = #{enabled}, update_time = now() " +
            "WHERE rule_id = #{ruleId} AND deleted = 0")
    int toggleEnabled(@Param("ruleId") String ruleId, @Param("enabled") boolean enabled);
}
