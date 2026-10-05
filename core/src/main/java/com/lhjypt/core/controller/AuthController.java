package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.common.auth.ConfirmTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 鉴权辅助 API（P1）
 * 提供敏感操作二次确认令牌的签发入口（PRD §3.5）。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final ConfirmTokenService confirmTokenService;

    /**
     * 签发一次性确认令牌（30秒有效，用后即废）
     */
    @PostMapping("/confirm-token")
    public Result<Map<String, String>> issueConfirmToken(
            @RequestHeader(value = "X-User-Role", required = false, defaultValue = "TRADER") String role) {
        String token = confirmTokenService.issue(role);
        return Result.success(Map.of(
                "token", token,
                "ttlSeconds", "30",
                "usage", "在敏感操作请求头中携带 X-Confirm-Token: " + token));
    }
}
