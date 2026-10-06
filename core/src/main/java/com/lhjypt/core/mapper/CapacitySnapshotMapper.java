package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.capacity.CapacitySnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CapacitySnapshotMapper extends BaseMapper<CapacitySnapshot> {

    @Select("SELECT * FROM capacity_snapshot ORDER BY sample_time DESC, id DESC LIMIT 1")
    CapacitySnapshot selectLatest();

    @Select("SELECT * FROM capacity_snapshot WHERE sample_time >= #{from} "
            + "AND sample_time <= #{to} ORDER BY sample_time DESC")
    List<CapacitySnapshot> selectHistory(@Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to);
}