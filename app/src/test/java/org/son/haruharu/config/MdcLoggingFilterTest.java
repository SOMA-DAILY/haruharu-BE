package org.son.haruharu.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;

/**
 * traceId는 클라이언트가 보내는 헤더에서 온다(#259 1-2).
 * ALB는 클라가 보낸 Root=를 지우지 않고 Self=만 앞에 붙이므로, 헤더 값을 그대로 믿으면
 * 로그의 [traceId/userId] 구조를 깨는 위조가 가능하다(CWE-117).
 */
class MdcLoggingFilterTest {

    private static final String VALID_ROOT = "1-67891233-abcdef012345678912345678";

    private final MdcLoggingFilter filter = new MdcLoggingFilter();

    /** 필터 체인 실행 시점의 MDC traceId를 캡처한다 — 필터가 finally에서 MDC.clear()하므로 안에서 봐야 한다. */
    private String traceIdSeenByChain(String header) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        given(request.getHeader("X-Amzn-Trace-Id")).willReturn(header);

        AtomicReference<String> captured = new AtomicReference<>();
        willAnswer(invocation -> {
            captured.set(MDC.get(MdcLoggingFilter.TRACE_ID));
            return null;
        }).given(chain).doFilter(request, response);

        filter.doFilterInternal(request, response, chain);
        return captured.get();
    }

    @Test
    @DisplayName("ALB가 주입한 정상 X-Amzn-Trace-Id는 Root 값을 그대로 traceId로 쓴다")
    void givenValidAlbTraceHeader_whenFilter_thenUsesRootValue() throws Exception {
        assertThat(traceIdSeenByChain("Root=" + VALID_ROOT)).isEqualTo(VALID_ROOT);
    }

    @Test
    @DisplayName("ALB가 Self를 앞에 붙인 형태에서도 Root 값만 추출해 traceId로 쓴다")
    void givenSelfPrefixedTraceHeader_whenFilter_thenExtractsRootValue() throws Exception {
        String header = "Self=1-6a587019-1234567890abcdef12345678;Root=" + VALID_ROOT;
        assertThat(traceIdSeenByChain(header)).isEqualTo(VALID_ROOT);
    }

    @Test
    @DisplayName("브래킷·슬래시로 로그 구조를 깨려는 조작 헤더는 traceId에 반영되지 않는다")
    void givenBracketInjectionHeader_whenFilter_thenFallsBackToGeneratedId() throws Exception {
        String traceId = traceIdSeenByChain("Root=1-x/999] ERROR o.s.h.FAKE:INJECTED [");

        assertThat(traceId).doesNotContain("]", "[", "/", "ERROR", "FAKE", "INJECTED");
    }

    @Test
    @DisplayName("Root 형식에 맞지 않는 스푸핑 값은 traceId로 쓰이지 않는다")
    void givenSpoofedRootValue_whenFilter_thenFallsBackToGeneratedId() throws Exception {
        String traceId = traceIdSeenByChain("Root=1-11111111-CLAUDESPOOF00000000000000");

        assertThat(traceId).doesNotContain("CLAUDESPOOF");
    }

    @Test
    @DisplayName("2000자짜리 X-Amzn-Trace-Id를 보내도 traceId는 64자 이하로 제한된다")
    void givenOversizedTraceHeader_whenFilter_thenFallsBackAndStaysBounded() throws Exception {
        // 별도의 길이 제한 로직은 없다 — 화이트리스트가 형식(35자 고정)을 강제하므로
        // 불일치는 UUID 폴백(8자)으로, 일치는 35자로 끝나 상한이 자동으로 지켜진다.
        String traceId = traceIdSeenByChain("Root=1-" + "a".repeat(2000));

        assertThat(traceId).hasSizeLessThanOrEqualTo(64);
        assertThat(traceId).doesNotContain("aaaa");
    }

    @Test
    @DisplayName("Root 값 뒤에 덧붙인 이스케이프 문자열은 traceId에 섞이지 않는다")
    void givenSuffixedTraceHeader_whenFilter_thenKeepsOnlyRootValue() throws Exception {
        String traceId = traceIdSeenByChain("Root=" + VALID_ROOT + "[31mFAKE");

        assertThat(traceId).isEqualTo(VALID_ROOT);
    }

    @Test
    @DisplayName("ALB를 거치지 않아 헤더가 없으면 자체 생성 traceId를 쓴다")
    void givenNoTraceHeader_whenFilter_thenGeneratesId() throws Exception {
        String traceId = traceIdSeenByChain(null);

        assertThat(traceId).isNotBlank();
    }

    @Test
    @DisplayName("요청 종료 후 MDC가 비워진다 — 스레드풀 재사용 시 이전 요청의 userId가 새면 안 된다")
    void givenRequestCompleted_whenFilterReturns_thenMdcCleared() throws Exception {
        traceIdSeenByChain("Root=" + VALID_ROOT);

        assertThat(MDC.get(MdcLoggingFilter.TRACE_ID)).isNull();
        assertThat(MDC.get(MdcLoggingFilter.USER_ID)).isNull();
    }

    @Test
    @DisplayName("헬스체크 경로도 필터를 거쳐 MDC가 비워진다 — 제외하면 JwtAuthenticationFilter가 넣은 userId가 스레드에 잔류한다")
    void givenActuatorRequest_whenDoFilter_thenFilterRunsAndMdcCleared() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        given(request.getRequestURI()).willReturn("/actuator/health");

        AtomicReference<String> userIdSeenByChain = new AtomicReference<>();
        willAnswer(invocation -> {
            userIdSeenByChain.set(MDC.get(MdcLoggingFilter.USER_ID));
            return null;
        }).given(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        // 필터가 제외되지 않고 실제로 실행됐다는 증거
        assertThat(userIdSeenByChain.get()).isEqualTo(MdcLoggingFilter.ANONYMOUS);
        // 그래서 finally의 MDC.clear()가 돈다
        assertThat(MDC.get(MdcLoggingFilter.USER_ID)).isNull();
    }
}
