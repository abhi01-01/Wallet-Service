package com.wallet.walletservice.config;

import com.wallet.walletservice.domain.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;


@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationsMs;

    private SecretKey getSigningKey(){
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(User user){
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpirationsMs) ;

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("ownerType", user.getOwnerType())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    public Claims validateAndParseClaims(String token){
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUserIdFromToken(String token){
        return validateAndParseClaims(token).getSubject() ;
    }

    public String getOwnerTypeFromToken(String token){
        return validateAndParseClaims(token).get("ownerType", String.class);
    }

    public boolean isTokenValid(String token){
        try {
            validateAndParseClaims(token);
            return true;
        }catch (JwtException | IllegalArgumentException ex){
            log.warn("Invalid JWT token: {}", ex.getMessage());
            return false;
        }
    }
}
