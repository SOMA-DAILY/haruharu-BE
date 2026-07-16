package org.son.haruharu.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 요청 단위 식별자(traceId·userId)를 MDC에 심어 모든 로그에 자동 부착한다(#257).
 *
 * traceId는 ALB가 주입하는 X-Amzn-Trace-Id의 Root 값이다 — ALB access log와 같은 키라 조인이 된다.
 * 다만 ALB는 클라이언트가 보낸 Root=를 지우지 않고 Self=만 앞에 붙이므로 이 값은 클라 통제 하에 있다.
 * 그대로 MDC에 넣으면 logging.pattern.level의 [traceId/userId] 구조를 ']'·'/'로 깨고 가짜 내용을
 * 심을 수 있어(CWE-117) X-Ray 형식만 화이트리스트로 통과시킨다(#259). 형식이 길이도 고정한다.
 *
 * 순서: Security 체인(-100) 안에서 끝나는 401·403 로그에도 traceId가 필요해 그보다 앞에 둔다.
 * HIGHEST_PRECEDENCE는 Boot의 request-wrapper 필터(OrderedCharacterEncodingFilter)와 같은 값이라
 * 순서가 미지정이 되므로 비켜 앉는다. 인증 전이라 userId는 여기서 못 채운다 — JwtAuthenticationFilter가 넣는다.
 *
 * 어떤 경로도 제외하지 않는다 — 제외하면 MDC.clear()가 실행되지 않아 userId가 스레드에 잔류한다(#259).
 * 로그량 억제는 실제로 로그를 찍는 AccessLogFilter의 책임이다.
 *
 * 동기 요청 전제 — ASYNC 디스패치에선 실행되지 않아(shouldNotFilterAsyncDispatch 기본 true)
 * SSE 도입 시 async 스레드 로그에서 traceId가 사라진다. 그땐 AsyncListener.onComplete 기반으로 옮겨야 한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_HEADER = "X-Amzn-Trace-Id";
    static final String TRACE_ID = "traceId";
    static final String USER_ID = "userId";
    /** 인증되지 않은 요청의 userId 자리. logging.pattern.level의 [traceId/userId] 자리를 비우지 않는다. */
    static final String ANONYMOUS = "-";

    /** X-Ray 트레이스 ID 형식(Root=1-{8 hex}-{24 hex}). Self=...;Root=... 형태에서도 Root 값만 뽑는다. */
    private static final Pattern ROOT_PATTERN = Pattern.compile("Root=(1-[0-9a-f]{8}-[0-9a-f]{24})");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            MDC.put(TRACE_ID, resolveTraceId(request));
            // 인증 전이라 아직 모른다. 인증에 성공하면 JwtAuthenticationFilter가 덮어쓴다.
            MDC.put(USER_ID, ANONYMOUS);
            filterChain.doFilter(request, response);
        } finally {
            // 스레드풀 재사용 시 이전 요청의 값이 새는 것을 막는다.
            MDC.clear();
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String header = request.getHeader(TRACE_HEADER);
        if (header != null) {
            Matcher matcher = ROOT_PATTERN.matcher(header);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }

}
