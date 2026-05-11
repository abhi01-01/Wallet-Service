package com.wallet.walletservice.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    private String extractToken(HttpServletRequest request){
        String bearer = request.getHeader("Authorization");

        if(StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")){
            return bearer.substring(7) ;
        }
        return null;
    }

    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException{
        String token = extractToken(request) ;

        if(StringUtils.hasText(token) && jwtTokenProvider.isTokenValid(token)){
            try {
                Claims claims = jwtTokenProvider.validateAndParseClaims(token);
                String userId = claims.getSubject();
                String ownerType = claims.get("ownerType", String.class);

                // Build authority from the ownerType — "USER" → "ROLE_USER", "SYSTEM" → "ROLE_SYSTEM"
                List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + ownerType));

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("JWT auth set for userId={}, ownerType={}", userId, ownerType);

            }catch (Exception ex){
                log.warn("Could not set user authentication from JWT: {}", ex.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

}
