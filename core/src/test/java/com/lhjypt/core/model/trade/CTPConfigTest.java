package com.lhjypt.core.model.trade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CTPConfigTest {

    @Test
    void toStringDoesNotExposeCredentials() {
        CTPConfig config = new CTPConfig();
        config.setBrokerId("9999");
        config.setUserId("trader-001");
        config.setPassword("super-secret");
        config.setAuthCode("auth-secret");

        String text = config.toString();

        assertFalse(text.contains("super-secret"));
        assertFalse(text.contains("auth-secret"));
        assertTrue(text.contains("credentials=<redacted>"));
    }
}
