package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.risk.IntradayRiskRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 事中风控规则数据访问层（P1）
 */
@Mapper
public interface IntradayRiskRuleMapper extends BaseMapper<IntradayRiskRule> {

    @Select("SELECT * FROM intraday_risk_rule WHERE rule_id = #{ruleId} AND deleted = 0")
    IntradayRiskRule selectByRuleId(@Param("ruleId") String ruleId);

    @Select("SELECT * FROM intraday_risk_rule WHERE enabled = true AND deleted = 0")
    List<IntradayRiskRule> selectEnabledRules();
}