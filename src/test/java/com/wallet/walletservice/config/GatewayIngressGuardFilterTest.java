package com.wallet.walletservice.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayIngressGuardFilterTest {

    private GatewayIngressGuardFilter filter;

    @BeforeEach
    void setUp() {
        filter = new GatewayIngressGuardFilter();
        ReflectionTestUtils.setField(filter, "expectedSecret", "edge-secret");
        ReflectionTestUtils.setField(filter, "obscureBasePath", "/internal-probe");
        ReflectionTestUtils.setField(filter, "publicActuatorEndpoints", "health,prometheus");
    }

    @Test
    void doFilterInternal_WhenGatewayTokenIsMissing_BlocksDirectTraffic() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/wallets/user-1/balance");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("Direct access forbidden"));
    }

    @Test
    void doFilterInternal_WhenGatewayTokenMatches_AllowsRequestThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/wallets/user-1/balance");
        request.addHeader("X-Gateway-Token", "edge-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void doFilterInternal_WhenHealthProbePathMatches_AllowsRequestWithoutGatewayToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal-probe/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void doFilterInternal_WhenHealthSubPathMatches_AllowsRequestWithoutGatewayToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal-probe/health/liveness");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void doFilterInternal_WhenPrometheusPathMatches_AllowsRequestWithoutGatewayToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal-probe/prometheus");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }
}
