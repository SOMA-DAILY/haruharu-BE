package org.son.haruharu.admin.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.son.haruharu.admin.domain.account.implement.AdminRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * 어드민 전용 JWT 발급/검증. subject=adminId, {@code role} claim에 역할을 담는다.
 * 유저 JWT와 <b>별도 시크릿</b>({@code ${admin.jwt.secret}})을 써서 어드민 토큰이 유저 서버에서,
 * 유저 토큰이 어드민 서버에서 통하지 않게 한다.
 */
@Component
public class AdminJwtProvider {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ROLE = "role";
    private static final String TYPE_ACCESS = "ACCESS";
    private static final String TYPE_REFRESH = "REFRESH";

    private final SecretKey secretKey;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;

    public AdminJwtProvider(
            @Value("${admin.jwt.secret}") String secret,
            @Value("${admin.jwt.access-token-expiry-ms}") long accessTokenExpiryMs,
            @Value("${admin.jwt.refresh-token-expiry-ms}") long refreshTokenExpiryMs
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMs = accessTokenExpiryMs;
        this.refreshTokenExpiryMs = refreshTokenExpiryMs;
    }

    public String generateAccessToken(Long adminId, AdminRole role) {
        return buildToken(adminId, role, accessTokenExpiryMs, TYPE_ACCESS);
    }

    public String generateRefreshToken(Long adminId, AdminRole role) {
        return buildToken(adminId, role, refreshTokenExpiryMs, TYPE_REFRESH);
    }

    public Long extractAdminId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    public AdminRole extractRole(String token) {
        return AdminRole.valueOf(parseClaims(token).get(CLAIM_ROLE, String.class));
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
            return TYPE_ACCESS.equals(parseClaims(token).get(CLAIM_TYPE));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private String buildToken(Long adminId, AdminRole role, long expiryMs, String type) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(adminId.toString())
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_ROLE, role.name())
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
