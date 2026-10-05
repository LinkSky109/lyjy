package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.common.auth.ConfirmTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AuthController 确认令牌签发端点测试（P1）
 */
class AuthControllerTest {

    @Test
    @DisplayName("签发端点返回可用的确认令牌")
    void issueEndpointReturnsUsableToken() {
        ConfirmTokenService tokenService = new ConfirmTokenService();
        AuthController controller = new AuthController(tokenService);

        Result<Map<String, String>> result = controller.issueConfirmToken("RISK_OFFICER");

        assertThat(result.getCode()).isZero();
        String token = result.getData().get("token");
        assertThat(token).isNotBlank();
        assertThat(tokenService.validate(token)).isTrue();
    }
}
