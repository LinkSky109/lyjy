package com.lhjypt.core.common.tenant;

/**
 * 租户上下文（P2 多租户）。
 * 由 AuthInterceptor 从 {@code X-Tenant-Id} 注入，默认 DEFAULT；请求结束清理。
 */
public final class TenantContextHolder {

    public static final String DEFAULT_TENANT = "DEFAULT";

    private static final ThreadLocal<String> TENANT = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void set(String tenantId) {
        TENANT.set(tenantId == null || tenantId.isBlank() ? DEFAULT_TENANT : tenantId);
    }

    public static String get() {
        String value = TENANT.get();
        return value == null || value.isBlank() ? DEFAULT_TENANT : value;
    }

    public static void clear() {
        TENANT.remove();
    }
}