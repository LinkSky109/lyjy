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

    @Select("<script>SELECT * FROM fund_flow WHERE create_time BETWEEN #{start} AND #{end} "
            + "<if test='accountId != null'> AND account_id = #{accountId}</if> "
            + "<if test='bizType != null'> AND biz_type = #{bizType}</if> "
            + "ORDER BY create_time DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<FundFlow> selectPageFiltered(@Param("accountId") String accountId,
                                      @Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end,
                                      @Param("bizType") String bizType,
                                      @Param("offset") long offset,
                                      @Param("limit") int limit);

    @Select("<script>SELECT COUNT(*) FROM fund_flow WHERE create_time BETWEEN #{start} AND #{end} "
            + "<if test='accountId != null'> AND account_id = #{accountId}</if> "
            + "<if test='bizType != null'> AND biz_type = #{bizType}</if></script>")
    long countFiltered(@Param("accountId") String accountId,
                       @Param("start") LocalDateTime start,
                       @Param("end") LocalDateTime end,
                       @Param("bizType") String bizType);

    @Select("SELECT * FROM fund_flow WHERE create_time >= #{start} "
            + "AND create_time < #{end} ORDER BY create_time")
    List<FundFlow> selectByTimeRange(@Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);
}
