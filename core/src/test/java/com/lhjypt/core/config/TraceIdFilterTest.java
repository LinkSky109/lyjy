package com.lhjypt.core.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TraceIdFilterTest {

    @Test
    void propagatesProvidedTraceIdAndClearsMdc() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/trade/status");
        request.addHeader("X-Trace-Id", "trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceInChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) ->
                traceInChain.set(MDC.get("traceId")));

        assertEquals("trace-123", traceInChain.get());
        assertEquals("trace-123", response.getHeader("X-Trace-Id"));
        assertEquals(null, MDC.get("traceId"));
    }

    @Test
    void generatesTraceIdWhenMissing() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/trade/status"),
                response, (req, res) -> {
                });

        assertNotNull(response.getHeader("X-Trace-Id"));
    }
}
