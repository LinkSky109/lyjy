package com.lhjypt.core.controller;

import com.lhjypt.core.common.Result;
import com.lhjypt.core.service.dashboard.DashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardControllerTest {

    @Test
    @DisplayName("GET /api/dashboard/summary 透传聚合结果")
    void summaryReturnsServicePayload() {
        DashboardService service = mock(DashboardService.class);
        when(service.summary()).thenReturn(Map.of("service", Map.of("status", "UP")));

        Result<Map<String, Object>> result = new DashboardController(service).summary();

        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).containsKey("service");
    }
}
