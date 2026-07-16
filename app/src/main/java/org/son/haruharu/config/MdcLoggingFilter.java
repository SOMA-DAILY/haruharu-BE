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
 * 요청 단위 식별자를 MDC에 심어 모든 로그에 자동 부착한다(#257).
 * 로그를 직접 남기지 않는다 — access log는 AccessLogFilter가, 예외 상세는 GlobalExceptionHandler가
 * 담당한다. 이 필터는 둘을 잇는 키만 제공한다.
 *
 * traceId는 ALB가 주입하는 X-Amzn-Trace-Id의 Root 값을 쓴다. ALB access log에도 같은 값이 남으므로
 * 두 로그를 같은 키로 조인할 수 있다 — 자체 UUID를 쓰면 이 연결이 끊긴다.
 *
 * 헤더를 그대로 신뢰하지 않고 X-Ray Root 형식만 화이트리스트로 통과시킨다(#259).
 * ALB는 클라이언트가 보낸 Root=를 지우지 않고 Self=만 앞에 붙이므로 이 값은 클라이언트 통제 하에 있다.
 * 무검증으로 MDC에 넣으면 logging.pattern.level의 [traceId/userId] 구조를 ']'·'/'로 깨고
 * 가짜 내용을 심을 수 있다(CWE-117). 형식이 길이를 고정하므로 길이 상한도 함께 충족된다.
 * 형식 불일치·헤더 없음(로컬·헬스체크)은 자체 UUID로 대체한다.
 *
 * 필터 체인의 앞쪽에 둔다 — Security 필터체인(order -100) 안에서 끝나는
 * 401·403 로그에도 traceId가 붙어야 하고, AccessLogFilter도 이 값을 쓰기 때문이다(#259).
 * HIGHEST_PRECEDENCE 자리는 Boot 이 request-wrapper 필터(OrderedCharacterEncodingFilter 등)에
 * 쓰므로 비켜 앉는다 — 같은 order 는 순서가 빈 등록 순서에 좌우돼 미지정이 된다.
 *
 * 동기 요청을 전제한다. SSE·DeferredResult 를 도입하면 ASYNC 디스패치에서 이 필터가 실행되지 않아
 * (shouldNotFilterAsyncDispatch 기본 true) 최초 디스패치 반환 시 MDC.clear()가 돌고
 * async 스레드의 로그에서 traceId 가 사라진다. 그때는 AsyncListener.onComplete 기반으로 옮겨야 한다.
 * 그래서 userId는 여기서 채울 수 없다(아직 인증 전) — 인증에 성공한 JwtAuthenticationFilter가 직접 넣는다.
 * 인증되지 않은 요청은 "-"로 남는다.
 *
 * 어떤 경로도 제외하지 않는다. 이 필터는 로그를 남기지 않으므로 제외해도 로그량이 줄지 않는 반면,
 * 제외된 경로에서는 MDC.clear()가 실행되지 않아 JwtAuthenticationFilter가 넣은 userId가
 * 스레드에 잔류한다(#259). MDC 수명주기는 이 필터가 단독으로 소유한다.
 * 로그량 억제(헬스체크 제외)는 실제로 로그를 찍는 AccessLogFilter의 책임이다.
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
