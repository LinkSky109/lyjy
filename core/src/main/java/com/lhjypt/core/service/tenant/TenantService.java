package com.lhjypt.core.service.tenant;

import com.lhjypt.core.mapper.TenantMapper;
import com.lhjypt.core.model.tenant.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 租户注册表与配额（P2 多租户隔离）。
 */
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantMapper tenantMapper;

    public List<Tenant> list() {
        return tenantMapper.selectAll();
    }

    public Tenant detail(String tenantId) {
        return tenantMapper.selectByTenantId(tenantId);
    }

    @Transactional
    public Tenant create(Tenant tenant, String operator) {
        if (tenant == null || tenant.getTenantId() == null || tenant.getName() == null) {
            throw new IllegalArgumentException("tenantId/name 不能为空");
        }
        Tenant existing = tenantMapper.selectByTenantId(tenant.getTenantId());
        if (existing != null) {
            throw new IllegalStateException("租户已存在: " + tenant.getTenantId());
        }
        tenant.setStatus(tenant.getStatus() == null ? Tenant.ACTIVE : tenant.getStatus());
        tenant.setMaxStrategies(tenant.getMaxStrategies() == null ? 100 : tenant.getMaxStrategies());
        tenant.setMaxPortfolios(tenant.getMaxPortfolios() == null ? 100 : tenant.getMaxPortfolios());
        tenant.setMaxAccounts(tenant.getMaxAccounts() == null ? 100 : tenant.getMaxAccounts());
        tenant.setMaxDailyOrders(tenant.getMaxDailyOrders() == null ? 10000 : tenant.getMaxDailyOrders());
        tenant.setRemark(tenant.getRemark());
        tenant.setCreateTime(LocalDateTime.now());
        tenant.setUpdateTime(LocalDateTime.now());
        tenantMapper.insert(tenant);
        return tenant;
    }

    @Transactional
    public Tenant updateQuota(String tenantId, Integer maxStrategies,
                              Integer maxPortfolios,
                              Integer maxAccounts, Integer maxDailyOrders) {
        Tenant tenant = require(tenantId);
        if (maxStrategies != null) {
            tenant.setMaxStrategies(maxStrategies);
        }
        if (maxPortfolios != null) {
            tenant.setMaxPortfolios(maxPortfolios);
        }
        if (maxAccounts != null) {
            tenant.setMaxAccounts(maxAccounts);
        }
        if (maxDailyOrders != null) {
            tenant.setMaxDailyOrders(maxDailyOrders);
        }
        tenant.setUpdateTime(LocalDateTime.now());
        tenantMapper.updateById(tenant);
        return tenant;
    }

    /** 校验租户存在且启用；DEFAULT 视为始终启用。 */
    public Tenant requireActive(String tenantId) {
        if (tenantId == null || tenantId.isBlank()
                || "DEFAULT".equalsIgnoreCase(tenantId)) {
            return null;
        }
        Tenant tenant = tenantMapper.selectByTenantId(tenantId);
        if (tenant == null || !Tenant.ACTIVE.equals(tenant.getStatus())) {
            throw new IllegalStateException("租户不存在或已停用: " + tenantId);
        }
        return tenant;
    }

    private Tenant require(String tenantId) {
        Tenant tenant = tenantMapper.selectByTenantId(tenantId);
        if (tenant == null) {
            throw new IllegalArgumentException("租户不存在: " + tenantId);
        }
        return tenant;
    }
}