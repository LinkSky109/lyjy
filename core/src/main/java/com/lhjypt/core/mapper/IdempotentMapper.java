package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.order.IdempotentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 幂等记录数据访问层
 */
@Mapper
public interface IdempotentMapper extends BaseMapper<IdempotentRecord> {

    @Select("SELECT * FROM order_idempotent WHERE idempotent_key = #{key}")
    IdempotentRecord selectByKey(@Param("key") String key);
}
