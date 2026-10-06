package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.tenant.Tenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {

    @Select("SELECT * FROM tenant WHERE tenant_id = #{tenantId} AND deleted = 0")
    Tenant selectByTenantId(@Param("tenantId") String tenantId);

    @Select("SELECT * FROM tenant WHERE deleted = 0 ORDER BY tenant_id")
    List<Tenant> selectAll();
}