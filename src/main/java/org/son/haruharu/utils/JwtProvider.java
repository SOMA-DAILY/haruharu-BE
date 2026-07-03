package org.son.haruharu.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;

    public JwtProvider(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.access-token-expiry-ms}") long accessTokenExpiryMs,
        @Value("${jwt.refresh-token-expiry-ms}") long refreshTokenExpiryMs
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMs = accessTokenExpiryMs;
        this.refreshTokenExpiryMs = refreshTokenExpiryMs;
    }

    public String generateAccessToken(Long userId) {
        return buildToken(userId, accessTokenExpiryMs, "ACCESS");
    }

    public String generateRefreshToken(Long userId) {
        return buildToken(userId, refreshTokenExpiryMs, "REFRESH");
    }

    public long getRefreshTokenExpiryMs() {
        return refreshTokenExpiryMs;
    }

    public Long extractUserId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        try {
            return "ACCESS".equals(parseClaims(token).get("type"));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private String buildToken(Long userId, long expiryMs, String type) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(userId.toString())
            .claim("type", type)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(expiryMs)))
            .signWith(secretKey)
            .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
