package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.degrade.DegradeSwitch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 降级开关数据访问（P1）
 */
@Mapper
public interface DegradeSwitchMapper extends BaseMapper<DegradeSwitch> {

    @Select("SELECT * FROM degrade_switch WHERE switch_key = #{key} AND deleted = 0")
    DegradeSwitch selectByKey(@Param("key") String key);
}
