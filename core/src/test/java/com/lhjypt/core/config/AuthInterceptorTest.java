package com.lhjypt.core.config;

import com.lhjypt.core.common.auth.UserContext;
import com.lhjypt.core.common.auth.UserContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthInterceptorTest {

    private final AuthInterceptor interceptor = new AuthInterceptor();

    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
    }

    @Test
    void viewerCannotWrite() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/trade/orders");
        request.addHeader("X-User-Role", UserContext.ROLE_VIEWER);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
    }

    @Test
    void traderCanSubmitOrderButCannotChangeDegradeSwitch() throws Exception {
        MockHttpServletRequest orderRequest = request("POST", "/api/trade/orders");
        orderRequest.addHeader("X-User-Role", UserContext.ROLE_TRADER);
        assertTrue(interceptor.preHandle(
                orderRequest, new MockHttpServletResponse(), new Object()));

        UserContextHolder.clear();
        MockHttpServletRequest degradeRequest = request("POST", "/api/degrade/switch");
        degradeRequest.addHeader("X-User-Role", UserContext.ROLE_TRADER);
        MockHttpServletResponse degradeResponse = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(degradeRequest, degradeResponse, new Object()));
        assertEquals(403, degradeResponse.getStatus());
    }

    @Test
    void adminCanChangeDegradeSwitch() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/degrade/switch");
        request.addHeader("X-User-Role", UserContext.ROLE_ADMIN);

        assertTrue(interceptor.preHandle(
                request, new MockHttpServletResponse(), new Object()));
        assertEquals(UserContext.ROLE_ADMIN, UserContextHolder.get().role());
    }

    @Test
    void onlyAdminCanManageGateways() throws Exception {
        MockHttpServletRequest traderRequest =
                request("POST", "/api/gateway/register");
        traderRequest.addHeader("X-User-Role", UserContext.ROLE_TRADER);
        MockHttpServletResponse traderResponse = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(traderRequest, traderResponse, new Object()));
        assertEquals(403, traderResponse.getStatus());

        UserContextHolder.clear();
        MockHttpServletRequest adminRequest =
                request("POST", "/api/gateway/register");
        adminRequest.addHeader("X-User-Role", UserContext.ROLE_ADMIN);
        assertTrue(interceptor.preHandle(
                adminRequest, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void onlyFundManagerCanWriteSettlement() throws Exception {
        MockHttpServletRequest traderRequest =
                request("POST", "/api/settlement/run");
        traderRequest.addHeader("X-User-Role", UserContext.ROLE_TRADER);
        MockHttpServletResponse traderResponse = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(traderRequest, traderResponse, new Object()));
        assertEquals(403, traderResponse.getStatus());

        UserContextHolder.clear();
        MockHttpServletRequest fundRequest =
                request("POST", "/api/settlement/run");
        fundRequest.addHeader("X-User-Role", UserContext.ROLE_FUND_MANAGER);
        assertTrue(interceptor.preHandle(
                fundRequest, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void settlementReadRequiresFundOrComplianceRole() throws Exception {
        MockHttpServletRequest traderRequest =
                request("GET", "/api/settlement/batches");
        traderRequest.addHeader("X-User-Role", UserContext.ROLE_TRADER);
        MockHttpServletResponse traderResponse = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(traderRequest, traderResponse, new Object()));
        assertEquals(403, traderResponse.getStatus());

        UserContextHolder.clear();
        MockHttpServletRequest complianceRequest =
                request("GET", "/api/settlement/batches");
        complianceRequest.addHeader("X-User-Role", UserContext.ROLE_COMPLIANCE);
        assertTrue(interceptor.preHandle(
                complianceRequest, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void viewerCanRead() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/monitor/alerts");
        request.addHeader("X-User-Role", UserContext.ROLE_VIEWER);

        assertTrue(interceptor.preHandle(
                request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void unknownRoleIsRejected() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/monitor/alerts");
        request.addHeader("X-User-Role", "SUPER_USER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }
}
