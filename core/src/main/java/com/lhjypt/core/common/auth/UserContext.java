package com.lhjypt.core.common.auth;

import java.util.Locale;
import java.util.Set;

/**
 * 当前请求用户上下文。
 */
public record UserContext(String userId, String role, String group) {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_RISK_OFFICER = "RISK_OFFICER";
    public static final String ROLE_TRADER = "TRADER";
    public static final String ROLE_FUND_MANAGER = "FUND_MANAGER";
    public static final String ROLE_COMPLIANCE = "COMPLIANCE";
    public static final String ROLE_VIEWER = "VIEWER";

    private static final Set<String> KNOWN_ROLES = Set.of(
            ROLE_ADMIN, ROLE_RISK_OFFICER, ROLE_TRADER,
            ROLE_FUND_MANAGER, ROLE_COMPLIANCE, ROLE_VIEWER);

    public static UserContext of(String userId, String role, String group) {
        String normalizedRole = role == null || role.isBlank()
                ? ROLE_VIEWER : role.trim().toUpperCase(Locale.ROOT);
        if (!KNOWN_ROLES.contains(normalizedRole)) {
            throw new IllegalArgumentException("未知用户角色: " + role);
        }
        return new UserContext(
                userId == null || userId.isBlank() ? "anonymous" : userId,
                normalizedRole,
                group == null ? "" : group);
    }

    public boolean hasAnyRole(String... roles) {
        for (String candidate : roles) {
            if (role.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    public boolean isViewer() {
        return ROLE_VIEWER.equals(role);
    }
}
