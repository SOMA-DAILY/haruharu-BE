package org.son.haruharu.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청 단위 식별자를 MDC에 심어 모든 로그에 자동 부착한다(#257).
 * 로그를 직접 남기지 않는다 — access log(method·path·status·duration)는 ALB가 담당하고,
 * 예외 상세는 GlobalExceptionHandler가 담당한다. 이 필터는 둘을 잇는 키만 제공한다.
 *
 * traceId는 ALB가 주입하는 X-Amzn-Trace-Id를 그대로 쓴다. ALB access log에도 같은 값이 남으므로
 * S3의 access log와 CloudWatch의 앱 로그를 같은 키로 조인할 수 있다 — 자체 UUID를 쓰면 이 연결이 끊긴다.
 * ALB를 거치지 않는 경로(로컬·헬스체크)만 UUID로 대체한다.
 *
 * userId는 JwtAuthenticationFilter가 인증을 마친 뒤에 채워지므로 이 필터가 그보다 뒤에 실행돼야 한다.
 * springSecurityFilterChain의 기본 order(-100)보다 뒤인 LOWEST_PRECEDENCE로 등록해 순서를 보장한다.
 * (Security 필터체인 안에서 끝나는 401은 EntryPoint가 직접 응답하므로 MDC 없이 나가며, 인증 전이라 userId도 없다.)
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_HEADER = "X-Amzn-Trace-Id";
    static final String TRACE_ID = "traceId";
    static final String USER_ID = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            MDC.put(TRACE_ID, resolveTraceId(request));
            MDC.put(USER_ID, resolveUserId());
            filterChain.doFilter(request, response);
        } finally {
            // 스레드풀 재사용 시 이전 요청의 값이 새는 것을 막는다.
            MDC.clear();
        }
    }

    /** 헬스체크는 traceId가 무의미하고 30초마다 쌓이므로 제외한다. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    private String resolveTraceId(HttpServletRequest request) {
        String header = request.getHeader(TRACE_HEADER);
        if (header != null && !header.isBlank()) {
            return header;
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** 인증 전(401)이거나 익명 요청이면 "-". principal은 JwtAuthenticationFilter가 넣은 userId(Long). */
    private String resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
            return "-";
        }
        return userId.toString();
    }
}
