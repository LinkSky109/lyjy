package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.fund.FundFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FundFlowMapper extends BaseMapper<FundFlow> {

    @Select("SELECT * FROM fund_flow WHERE account_id = #{accountId} " +
            "AND create_time BETWEEN #{start} AND #{end} ORDER BY create_time")
    List<FundFlow> selectByAccountAndTime(@Param("accountId") String accountId,
                                          @Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);
}
