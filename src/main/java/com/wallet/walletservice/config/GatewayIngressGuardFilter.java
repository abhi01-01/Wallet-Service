package com.wallet.walletservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Integer.MIN_VALUE)   // Guarantees this filter runs absolutely first in the entire servlet container
@Slf4j
public class GatewayIngressGuardFilter extends OncePerRequestFilter {
    @Value("${gateway.internal-secret:default-edge-secret-string-123}")
    private String expectedSecret;

    @Value("${management.endpoints.web.base-path:/actuator}")
    private String obscureBasePath;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {


        // Platform Whitelist: Match the exact hidden health check endpoint
        String requestUri = request.getRequestURI();
        String expectedHealthPath = obscureBasePath + "/health";
        if (requestUri.equals(expectedHealthPath)) {
            log.trace("Perimeter Guard Bypass: Verified platform health probe.");
            filterChain.doFilter(request, response);
            return;
        }

        // Perimeter Validation for Core Financial/Business APIs
        String incomingToken = request.getHeader("X-Gateway-Token");
        if (!StringUtils.hasText(incomingToken) || !expectedSecret.equals(incomingToken)) {
            log.warn("Security Alert: Direct public ingress attempt blocked from IP: {}. Path: {}",
                    request.getRemoteAddr(), request.getRequestURI());

            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Direct access forbidden. Access must route via API Gateway.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
