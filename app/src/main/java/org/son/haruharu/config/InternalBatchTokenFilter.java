package org.son.haruharu.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.batch.config.InternalBatchProperties;
import org.son.haruharu.common.ApiErrorDetail;
import org.son.haruharu.common.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 내부 배치 트리거 API(#389) 인증 필터. {@code X-Internal-Token} 공유 시크릿을 검증하고 통과 시
 * {@code ROLE_INTERNAL_BATCH} 권한을 부여한다. {@code /internal/**} 전용 시큐리티 체인 안에서만 동작한다
 * (InternalBatchSecurityConfig) — 빈으로 등록하지 않아 서블릿 전역 필터로 이중 등록되지 않는다.
 * <p>
 * 비교는 WebhookSecretVerifier 와 같은 방식: 양쪽을 SHA-256 으로 고정 길이화한 뒤 {@link MessageDigest#isEqual}
 * 상수시간 비교 — 길이·접두 일치 정보가 타이밍으로 새지 않는다.
 * <p>
 * <b>fail-closed</b>: 서버에 토큰이 설정돼 있지 않으면(빈 값) 어떤 헤더로도 통과하지 못한다. 설정 누락이
 * "인증 없는 내부 API" 로 조용히 이어지는 것을 막는다. 유저 JWT 는 이 경로의 인증 수단이 아니다.
 */
@Slf4j
public class InternalBatchTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";
    public static final String ROLE = "ROLE_INTERNAL_BATCH";
    static final String PRINCIPAL = "internal-batch";

    private final byte[] tokenDigest; // null = 미설정(fail-closed)
    private final JsonMapper jsonMapper;

    public InternalBatchTokenFilter(InternalBatchProperties properties, JsonMapper jsonMapper) {
        String token = properties.token();
        if (token == null || token.isBlank()) {
            log.warn("INTERNAL_BATCH_TOKEN 미설정 — /internal/batch/** 는 모든 요청을 거부한다(fail-closed)");
            this.tokenDigest = null;
        } else {
            this.tokenDigest = sha256(token);
        }
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        String reason = verify(presented);
        if (reason != null) {
            // 사유는 로그에만 — 응답으로 내면 토큰 대조에 악용된다(#259 와 같은 원칙).
            log.warn("401 {} {} code=UNAUTHORIZED reason={}", request.getMethod(), request.getRequestURI(), reason);
            writeUnauthorized(response, jsonMapper);
            return;
        }
        var auth = new UsernamePasswordAuthenticationToken(PRINCIPAL, null, List.of(new SimpleGrantedAuthority(ROLE)));
        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }

    /** @return 실패 사유(로그용). 통과면 null. */
    private String verify(String presented) {
        if (tokenDigest == null) {
            return "TOKEN_NOT_CONFIGURED";
        }
        if (presented == null || presented.isEmpty()) {
            return "NO_TOKEN";
        }
        return MessageDigest.isEqual(sha256(presented), tokenDigest) ? null : "TOKEN_MISMATCH";
    }

    /** 401 JSON 응답. 체인의 EntryPoint 도 같은 형식을 쓴다(InternalBatchSecurityConfig). */
    static void writeUnauthorized(HttpServletResponse response, JsonMapper jsonMapper) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<?> body = ApiResponse.error(new ApiErrorDetail("UNAUTHORIZED", "인증이 필요합니다."));
        response.getWriter().write(jsonMapper.writeValueAsString(body));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
