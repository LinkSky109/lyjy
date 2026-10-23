package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.fund.FundTransfer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FundTransferMapper extends BaseMapper<FundTransfer> {

    @Select("SELECT * FROM fund_transfer WHERE transfer_no = #{transferNo} LIMIT 1")
    FundTransfer selectByTransferNo(@Param("transferNo") String transferNo);

    @Select("SELECT * FROM fund_transfer WHERE idempotency_key = #{key} LIMIT 1")
    FundTransfer selectByIdempotencyKey(@Param("key") String key);
}
