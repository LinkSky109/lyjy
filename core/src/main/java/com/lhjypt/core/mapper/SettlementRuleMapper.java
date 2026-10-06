package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.settlement.SettlementRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SettlementRuleMapper extends BaseMapper<SettlementRule> {

    @Select("SELECT * FROM settlement_rule WHERE rule_id = #{ruleId} AND deleted = 0")
    SettlementRule selectByRuleId(@Param("ruleId") String ruleId);

    @Select("SELECT * FROM settlement_rule WHERE enabled = TRUE AND deleted = 0 "
            + "AND market = #{market} ORDER BY case when exchange_match is null then 1 else 0 end, id")
    List<SettlementRule> selectByMarket(@Param("market") String market);

    @Select("SELECT * FROM settlement_rule WHERE enabled = TRUE AND deleted = 0 "
            + "AND market = #{market} AND exchange_match = #{exchange} LIMIT 1")
    SettlementRule selectByMarketAndExchange(@Param("market") String market,
                                             @Param("exchange") String exchange);

    @Select("SELECT * FROM settlement_rule WHERE deleted = 0 ORDER BY market, id")
    List<SettlementRule> selectAll();
}