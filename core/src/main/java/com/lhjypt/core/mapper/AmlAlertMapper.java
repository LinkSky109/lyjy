package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.regulatory.AmlAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AmlAlertMapper extends BaseMapper<AmlAlert> {

    @Select("SELECT * FROM aml_alert WHERE alert_id = #{alertId} AND deleted = 0")
    AmlAlert selectByAlertId(@Param("alertId") String alertId);

    @Select("<script>"
            + "SELECT * FROM aml_alert WHERE deleted = 0 "
            + "<if test='status != null'> AND status = #{status}</if> "
            + "ORDER BY create_time DESC, id DESC LIMIT 500"
            + "</script>")
    List<AmlAlert> selectByStatus(@Param("status") String status);

    /** AML 去重：同规则 + 同账户（+策略 + 标的）存在 OPEN 告警则不再次生成。 */
    @Select("<script>"
            + "SELECT COUNT(*) FROM aml_alert WHERE deleted = 0 AND status = 'OPEN' "
            + "AND rule_code = #{ruleCode} AND account_id = #{accountId} "
            + "<if test='strategyId != null'> AND strategy_id = #{strategyId}</if> "
            + "<if test='symbol != null'> AND symbol = #{symbol}</if>"
            + "</script>")
    int countOpenDedupe(@Param("ruleCode") String ruleCode,
                        @Param("accountId") String accountId,
                        @Param("strategyId") String strategyId,
                        @Param("symbol") String symbol);
}