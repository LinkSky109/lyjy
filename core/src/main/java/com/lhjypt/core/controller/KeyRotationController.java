package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.common.auth.ConfirmTokenService;
import com.lhjypt.core.common.auth.UserContextHolder;
import com.lhjypt.core.service.strategy.KeyRotationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 策略密钥管理（P3-005，仅 ADMIN）：状态查询与密钥轮换（需一次性确认令牌）。
 */
@RestController
@RequestMapping("/api/strategy/keys")
@RequiredArgsConstructor
public class KeyRotationController {

    private final KeyRotationService keyRotationService;
    private final ConfirmTokenService confirmTokenService;

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(keyRotationService.status());
    }

    @PostMapping("/rotate")
    public Result<Map<String, Object>> rotate(
            @RequestParam String newKeyId,
            @RequestHeader(value = "X-Confirm-Token", required = false) String confirmToken,
            HttpServletRequest request) {
        if (!confirmTokenService.validate(confirmToken)) {
            return Result.error(409, "密钥轮换需要有效的二次确认令牌");
        }
        return Result.success(keyRotationService.rotate(newKeyId,
                UserContextHolder.get().userId(), request.getRemoteAddr()));
    }
}