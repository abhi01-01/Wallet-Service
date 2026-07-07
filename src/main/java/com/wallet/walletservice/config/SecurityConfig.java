package com.wallet.walletservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Arrays;
import java.util.stream.Stream;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final GatewayIdentityAuthenticationFilter gatewayIdentityAuthenticationFilter ;

    @Value("${management.endpoints.web.base-path:/actuator}")
    private String actuatorBasePath;

    @Value("${gateway.public-actuator-endpoints:health,prometheus}")
    private String publicActuatorEndpoints;

    private static final String[] PUBLIC_PATHS = { 
            "/", 
            "/api/v1/auth/signup", 
            "/api/v1/auth/verify-otp", 
            "/api/v1/auth/login", 
            "/api/v1/auth/resend-otp", 
            "/api/v1/auth/google", 
            "/api/v1/auth/refresh-token", 
            "/api/v1/auth/oauth2/success", 
            "/oauth2/**", 
            "/login/oauth2/**", 
            "/swagger-ui/**", 
            "/swagger-ui.html", 
            "/v3/api-docs/**", 
            "/api-docs/**", 
            "/api-docs.yaml", 
            "/swagger-resources/**", 
            "/configuration/**", 
            "/webjars/**", 
            "/api/v1/webhooks/**"         // FIX: Recursive wildcard allows Razorpay target endpoints
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, GatewayIngressGuardFilter gatewayIngressGuardFilter) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(gatewayIngressGuardFilter, org.springframework.security.web.header.HeaderWriterFilter.class)
                .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicActuatorPaths()).permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/wallets/topUp").hasRole("SYSTEM")
                        .requestMatchers(HttpMethod.POST, "/api/v1/wallets/bonus").hasRole("SYSTEM")
                        .requestMatchers(HttpMethod.POST, "/api/v1/wallets/spend").hasRole("USER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/wallets/**").hasAnyRole("USER","SYSTEM")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/messaging/**").hasRole("SYSTEM")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/users/**").hasRole("SYSTEM")
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/razorpay").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(gatewayIdentityAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private String[] publicActuatorPaths() {
        String normalizedBasePath = actuatorBasePath.startsWith("/")
                ? actuatorBasePath
                : "/" + actuatorBasePath;
        return Arrays.stream(publicActuatorEndpoints.split(","))
                .map(String::trim)
                .filter(endpoint -> !endpoint.isBlank())
                .flatMap(endpoint -> {
                    String endpointPath = normalizedBasePath + "/" + endpoint;
                    return Stream.of(endpointPath, endpointPath + "/**");
                })
                .toArray(String[]::new);
    }


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
