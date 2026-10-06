package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.regulatory.AmlRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AmlRuleMapper extends BaseMapper<AmlRule> {

    @Select("SELECT * FROM aml_rule WHERE enabled = TRUE AND deleted = 0 ORDER BY rule_code")
    List<AmlRule> selectEnabledRules();

    @Select("SELECT * FROM aml_rule WHERE rule_id = #{ruleId} AND deleted = 0")
    AmlRule selectByRuleId(@Param("ruleId") String ruleId);

    @Select("SELECT * FROM aml_rule WHERE rule_code = #{ruleCode} AND enabled = TRUE "
            + "AND deleted = 0 ORDER BY id LIMIT 1")
    AmlRule selectEnabledByCode(@Param("ruleCode") String ruleCode);
}