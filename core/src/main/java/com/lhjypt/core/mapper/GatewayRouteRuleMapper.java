package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.gateway.GatewayRouteRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GatewayRouteRuleMapper extends BaseMapper<GatewayRouteRule> {

    @Select("SELECT * FROM gateway_route_rule WHERE rule_id = #{ruleId} AND deleted = 0 LIMIT 1")
    GatewayRouteRule selectByRuleId(@Param("ruleId") String ruleId);

    @Select("SELECT * FROM gateway_route_rule WHERE enabled = true AND deleted = 0 "
            + "ORDER BY priority, id")
    List<GatewayRouteRule> selectEnabledRules();
}
