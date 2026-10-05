package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.FundAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FundAccountMapper extends BaseMapper<FundAccount> {

    @Select("SELECT * FROM fund_account WHERE account_id = #{accountId} AND deleted = 0")
    FundAccount selectByAccountId(@Param("accountId") String accountId);
}
