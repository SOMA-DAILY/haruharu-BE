package org.son.haruharu.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
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

    /**
     * 검증 결과를 사유와 함께 반환한다(#259). 사유는 로그 전용 — 응답으로 노출하면 계정 열거에 악용된다.
     * boolean 만 반환하던 isValid() 로는 EntryPoint 가 401 원인을 남길 수 없었다.
     */
    public JwtValidation validate(String token) {
        try {
            parseClaims(token);
            return JwtValidation.VALID;
        } catch (ExpiredJwtException e) {
            return JwtValidation.EXPIRED;
        } catch (SignatureException e) {
            return JwtValidation.BAD_SIGNATURE;
        } catch (JwtException | IllegalArgumentException e) {
            return JwtValidation.MALFORMED;
        }
    }

    public boolean isValid(String token) {
        return validate(token) == JwtValidation.VALID;
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
