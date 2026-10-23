package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.fund.CashTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CashTransactionMapper extends BaseMapper<CashTransaction> {

    @Select("<script>SELECT * FROM cash_transaction WHERE 1 = 1 "
            + "<if test='status != null'> AND status = #{status}</if> "
            + "ORDER BY create_time DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<CashTransaction> selectPageFiltered(@Param("status") String status,
                                             @Param("offset") long offset,
                                             @Param("limit") int limit);

    @Select("<script>SELECT COUNT(*) FROM cash_transaction WHERE 1 = 1 "
            + "<if test='status != null'> AND status = #{status}</if></script>")
    long countFiltered(@Param("status") String status);
}
