package com.lhjypt.core.config;

import com.alibaba.fastjson2.JSON;
import com.lhjypt.core.common.Result;
import com.lhjypt.core.common.auth.UserContext;
import com.lhjypt.core.common.auth.UserContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Set;

/**
 * 角色权限和写操作审计拦截器。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");
    private static final Set<String> WRITE_METHODS =
            Set.of("POST", "PUT", "DELETE", "PATCH");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws IOException {
        UserContext context;
        try {
            context = UserContext.of(
                    request.getHeader("X-User-Id"),
                    request.getHeader("X-User-Role"),
                    request.getHeader("X-User-Group"));
        } catch (IllegalArgumentException error) {
            return reject(response, error.getMessage());
        }
        UserContextHolder.set(context);

        String path = request.getRequestURI();
        if (!isProtected(path)) {
            return true;
        }
        if (!WRITE_METHODS.contains(request.getMethod())) {
            if (path.startsWith("/api/settlement/")
                    && !context.hasAnyRole(
                    UserContext.ROLE_FUND_MANAGER,
                    UserContext.ROLE_COMPLIANCE)) {
                return reject(response, "当前角色无权查看清算数据");
            }
            if (path.startsWith("/api/portfolio/")
                    && !context.hasAnyRole(
                    UserContext.ROLE_TRADER,
                    UserContext.ROLE_RISK_OFFICER,
                    UserContext.ROLE_COMPLIANCE)) {
                return reject(response, "当前角色无权查看组合数据");
            }
            return true;
        }
        if (context.isViewer()) {
            return reject(response, "VIEWER 角色不允许写操作");
        }
        if (!isAllowedWrite(context, path)) {
            return reject(response, "当前角色无权执行该操作: " + context.role());
        }

        audit.info("audit user={} role={} group={} method={} path={}",
                context.userId(), context.role(), context.group(),
                request.getMethod(), path);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContextHolder.clear();
    }

    private static boolean isProtected(String path) {
        return path.startsWith("/api/");
    }

    private static boolean isAllowedWrite(UserContext context, String path) {
        if (context.hasAnyRole(UserContext.ROLE_ADMIN)) {
            return true;
        }
        if (path.startsWith("/api/auth/")) {
            return true;
        }
        if (path.startsWith("/api/degrade/")) {
            return false;
        }
        if (path.startsWith("/api/gateway/")) {
            return false;
        }
        if (path.startsWith("/api/settlement/")) {
            return context.hasAnyRole(UserContext.ROLE_FUND_MANAGER);
        }
        if (path.startsWith("/api/portfolio")) {
            return context.hasAnyRole(UserContext.ROLE_TRADER);
        }
        if (path.startsWith("/api/data-governance/")) {
            return false;
        }
        if (path.startsWith("/api/fund/")) {
            return context.hasAnyRole(UserContext.ROLE_FUND_MANAGER);
        }
        if (path.startsWith("/api/risk/intraday/")
                || path.startsWith("/api/surveillance/")
                || path.startsWith("/api/monitor/")) {
            return context.hasAnyRole(
                    UserContext.ROLE_RISK_OFFICER, UserContext.ROLE_COMPLIANCE);
        }
        if (path.startsWith("/api/var/")) {
            return context.hasAnyRole(
                    UserContext.ROLE_RISK_OFFICER, UserContext.ROLE_TRADER);
        }
        if (path.startsWith("/api/strategy/")) {
            return context.hasAnyRole(UserContext.ROLE_TRADER);
        }
        if (path.startsWith("/api/trade/") || path.startsWith("/api/oms/")) {
            return context.hasAnyRole(
                    UserContext.ROLE_TRADER, UserContext.ROLE_RISK_OFFICER);
        }
        return context.hasAnyRole(
                UserContext.ROLE_TRADER,
                UserContext.ROLE_RISK_OFFICER,
                UserContext.ROLE_FUND_MANAGER,
                UserContext.ROLE_COMPLIANCE);
    }

    private static boolean reject(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(Result.error(403, message)));
        return false;
    }
}
