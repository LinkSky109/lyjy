package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.common.auth.UserContextHolder;
import com.lhjypt.core.model.tenant.Tenant;
import com.lhjypt.core.service.tenant.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 租户管理 API（P2 多租户隔离，仅 ADMIN）。
 */
@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @GetMapping("/list")
    public Result<List<Tenant>> list() {
        return Result.success(tenantService.list());
    }

    @GetMapping("/{tenantId}")
    public Result<Tenant> detail(@PathVariable String tenantId) {
        return Result.success(tenantService.detail(tenantId));
    }

    @PostMapping
    public Result<Tenant> create(@RequestBody Tenant tenant) {
        return Result.success(tenantService.create(tenant, UserContextHolder.get().userId()));
    }

    @PostMapping("/{tenantId}/quota")
    public Result<Tenant> updateQuota(@PathVariable String tenantId,
                                      @RequestParam(required = false) Integer maxStrategies,
                                      @RequestParam(required = false) Integer maxAccounts,
                                      @RequestParam(required = false) Integer maxDailyOrders) {
        return Result.success(tenantService.updateQuota(
                tenantId, maxStrategies, maxAccounts, maxDailyOrders));
    }
}