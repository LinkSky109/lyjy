package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.gateway.GatewayInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface GatewayInfoMapper extends BaseMapper<GatewayInfo> {

    @Select("SELECT * FROM gateway_info WHERE gateway_id = #{gatewayId} AND deleted = 0 LIMIT 1")
    GatewayInfo selectByGatewayId(@Param("gatewayId") String gatewayId);

    @Select("SELECT * FROM gateway_info WHERE deleted = 0 ORDER BY priority, gateway_id")
    List<GatewayInfo> selectAll();

    @Update("UPDATE gateway_info SET status = #{status}, last_health_at = #{healthAt}, "
            + "update_time = CURRENT_TIMESTAMP WHERE gateway_id = #{gatewayId} AND deleted = 0")
    int updateStatus(@Param("gatewayId") String gatewayId,
                     @Param("status") String status,
                     @Param("healthAt") LocalDateTime healthAt);
}
