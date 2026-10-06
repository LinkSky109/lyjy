package com.lhjypt.core.service.tenant;

import com.lhjypt.core.mapper.TenantMapper;
import com.lhjypt.core.model.tenant.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantServiceTest {

    private TenantMapper tenantMapper;
    private TenantService service;

    @BeforeEach
    void setUp() {
        tenantMapper = mock(TenantMapper.class);
        service = new TenantService(tenantMapper);
    }

    @Test
    @DisplayName("创建租户：默认启用并带配额")
    void createInsertsDefaultQuotas() {
        when(tenantMapper.selectByTenantId("T1")).thenReturn(null);
        Tenant tenant = new Tenant();
        tenant.setTenantId("T1");
        tenant.setName("租户一");

        Tenant created = service.create(tenant, "admin");

        assertThat(created.getStatus()).isEqualTo(Tenant.ACTIVE);
        assertThat(created.getMaxStrategies()).isEqualTo(100);
        assertThat(created.getMaxAccounts()).isEqualTo(100);
        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantMapper).insert(captor.capture());
        assertThat(captor.getValue().getCreateTime()).isNotNull();
    }

    @Test
    @DisplayName("重复创建抛异常")
    void createDuplicateThrows() {
        when(tenantMapper.selectByTenantId("T1")).thenReturn(new Tenant());
        assertThatThrownBy(() -> service.create(
                tenant("T1"), "admin")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("更新配额生效")
    void updateQuotaPersists() {
        Tenant existing = new Tenant();
        existing.setTenantId("T1");
        existing.setMaxStrategies(100);
        existing.setMaxAccounts(100);
        existing.setMaxDailyOrders(10000);
        when(tenantMapper.selectByTenantId("T1")).thenReturn(existing);

        Tenant updated = service.updateQuota("T1", 5, null, null);

        assertThat(updated.getMaxStrategies()).isEqualTo(5);
        assertThat(updated.getMaxAccounts()).isEqualTo(100);
        verify(tenantMapper).updateById(existing);
    }

    @Test
    @DisplayName("DEFAULT 租户始终可用；缺失/停用租户拒绝")
    void requireActiveRules() {
        assertThat(service.requireActive("DEFAULT")).isNull();

        when(tenantMapper.selectByTenantId("GONE")).thenReturn(null);
        assertThatThrownBy(() -> service.requireActive("GONE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("租户不存在");

        Tenant suspended = new Tenant();
        suspended.setTenantId("S1");
        suspended.setStatus(Tenant.SUSPENDED);
        when(tenantMapper.selectByTenantId("S1")).thenReturn(suspended);
        assertThatThrownBy(() -> service.requireActive("S1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("停用");
    }

    private static Tenant tenant(String tenantId) {
        Tenant tenant = new Tenant();
        tenant.setTenantId(tenantId);
        tenant.setName(tenantId);
        return tenant;
    }
}