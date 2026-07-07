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
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(Integer.MIN_VALUE)   // Guarantees this filter runs absolutely first in the entire servlet container
@Slf4j
public class GatewayIngressGuardFilter extends OncePerRequestFilter {
    @Value("${gateway.internal-secret}")
    private String expectedSecret;

    @Value("${management.endpoints.web.base-path:/actuator}")
    private String obscureBasePath;

    @Value("${gateway.public-actuator-endpoints:health,prometheus}")
    private String publicActuatorEndpoints;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {


        String requestUri = request.getRequestURI();
        if (isPublicActuatorEndpoint(requestUri)) {
            log.trace("Perimeter Guard Bypass: Verified actuator endpoint {}.", requestUri);
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

    private boolean isPublicActuatorEndpoint(String requestUri) {
        String normalizedBasePath = obscureBasePath.startsWith("/")
                ? obscureBasePath
                : "/" + obscureBasePath;

        return publicEndpointNames().stream()
                .map(endpoint -> normalizedBasePath + "/" + endpoint)
                .anyMatch(endpointPath -> requestUri.equals(endpointPath) || requestUri.startsWith(endpointPath + "/"));
    }

    private Set<String> publicEndpointNames() {
        return Arrays.stream(publicActuatorEndpoints.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }
}
