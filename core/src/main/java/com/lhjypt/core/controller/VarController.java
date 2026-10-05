package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.common.auth.UserContextHolder;
import com.lhjypt.core.model.var.VarCalculation;
import com.lhjypt.core.model.var.VarComputeRequest;
import com.lhjypt.core.model.var.VarResult;
import com.lhjypt.core.service.var.VarService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * VaR 风险价值 API（PRD §5.7）。
 */
@RestController
@RequestMapping("/api/var")
@RequiredArgsConstructor
public class VarController {

    private final VarService varService;

    @PostMapping("/compute")
    public Result<VarResult> compute(@RequestBody VarComputeRequest request) {
        return Result.success(varService.compute(request, UserContextHolder.get()));
    }

    @GetMapping("/latest")
    public Result<VarCalculation> latest(@RequestParam String scopeType,
                                         @RequestParam String scopeId) {
        return Result.success(varService.latest(scopeType, scopeId, UserContextHolder.get()));
    }

    @GetMapping("/history")
    public Result<List<VarCalculation>> history(
            @RequestParam String scopeType,
            @RequestParam String scopeId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return Result.success(varService.history(
                scopeType, scopeId, from, to, UserContextHolder.get()));
    }
}
