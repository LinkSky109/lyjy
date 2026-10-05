package com.lhjypt.core.common.auth;

/**
 * 请求级用户上下文。
 */
public final class UserContextHolder {

    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(UserContext context) {
        CONTEXT.set(context);
    }

    public static UserContext get() {
        UserContext context = CONTEXT.get();
        return context == null
                ? UserContext.of(null, UserContext.ROLE_VIEWER, null)
                : context;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
