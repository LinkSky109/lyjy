package com.lhjypt.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhjypt.core.model.portfolio.PortfolioRebalanceJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PortfolioRebalanceJobMapper extends BaseMapper<PortfolioRebalanceJob> {

    @Select("SELECT * FROM portfolio_rebalance_job WHERE job_id = #{jobId} AND deleted = 0")
    PortfolioRebalanceJob selectByJobId(@Param("jobId") String jobId);

    @Select("SELECT * FROM portfolio_rebalance_job WHERE portfolio_id = #{portfolioId} "
            + "AND status IN ('PENDING','PREVIEW_READY','RUNNING') AND deleted = 0 "
            + "ORDER BY create_time DESC LIMIT 1")
    PortfolioRebalanceJob selectActiveByPortfolio(@Param("portfolioId") String portfolioId);

    @Select("SELECT * FROM portfolio_rebalance_job WHERE portfolio_id = #{portfolioId} "
            + "AND deleted = 0 ORDER BY create_time DESC")
    List<PortfolioRebalanceJob> selectByPortfolioId(@Param("portfolioId") String portfolioId);
}
