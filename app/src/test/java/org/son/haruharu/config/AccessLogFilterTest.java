package org.son.haruharu.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 성공 요청(200)이 어디에도 기록되지 않던 구멍을 메운다(#259 2-3).
 * 기존 주석은 "access log는 ALB가 담당"이라고 했으나 haruharu-alb의 access_logs.s3.enabled=false 라
 * 전제가 성립하지 않았다. ALB 로그는 dev·prod 공용 ALB라 dev만 켤 수 없고 S3 배치라 약 5분 지연된다.
 */
class AccessLogFilterTest {

    private final AccessLogFilter filter = new AccessLogFilter();
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(AccessLogFilter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    private HttpServletRequest request(String method, String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        given(request.getMethod()).willReturn(method);
        given(request.getRequestURI()).willReturn(uri);
        return request;
    }

    @Test
    @DisplayName("성공 요청은 method·path·status·duration이 담긴 로그 1줄을 남긴다")
    void givenSuccessfulRequest_whenFilter_thenLogsAccessLine() throws Exception {
        HttpServletRequest request = request("GET", "/api/v1/users/me");
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        given(response.getStatus()).willReturn(200);

        filter.doFilterInternal(request, response, chain);

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage())
                .contains("GET")
                .contains("/api/v1/users/me")
                .contains("200")
                .contains("ms");
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("실패 응답도 status와 함께 access log에 남는다")
    void givenFailedRequest_whenFilter_thenLogsStatus() throws Exception {
        HttpServletRequest request = request("GET", "/api/v1/matching/apply");
        HttpServletResponse response = mock(HttpServletResponse.class);
        given(response.getStatus()).willReturn(403);

        filter.doFilterInternal(request, response, mock(FilterChain.class));

        assertThat(appender.list.getFirst().getFormattedMessage()).contains("403");
    }

    @Test
    @DisplayName("쿼리스트링은 access log에 남기지 않는다 — PII가 섞일 수 있다")
    void givenRequestWithQueryString_whenFilter_thenQueryNotLogged() throws Exception {
        HttpServletRequest request = request("GET", "/api/v1/rooms");
        given(request.getQueryString()).willReturn("email=user@test.com&phone=010-1234-5678");
        HttpServletResponse response = mock(HttpServletResponse.class);
        given(response.getStatus()).willReturn(200);

        filter.doFilterInternal(request, response, mock(FilterChain.class));

        String message = appender.list.getFirst().getFormattedMessage();
        assertThat(message).doesNotContain("user@test.com");
        assertThat(message).doesNotContain("010-1234-5678");
        assertThat(message).doesNotContain("email=");
    }

    @Test
    @DisplayName("체인에서 예외가 나도 access log를 남기고 예외는 그대로 전파한다")
    void givenChainThrows_whenFilter_thenLogsAndPropagates() throws Exception {
        HttpServletRequest request = request("POST", "/api/v1/matching/apply");
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        given(response.getStatus()).willReturn(500);
        willThrow(new IOException("boom")).given(chain).doFilter(request, response);

        // 삼키면 GlobalExceptionHandler가 처리하지 못하므로 전파를 계약으로 고정한다
        assertThatThrownBy(() -> filter.doFilterInternal(request, response, chain))
                .isInstanceOf(IOException.class)
                .hasMessage("boom");

        // 실패한 요청이야말로 기록이 필요하다
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getFormattedMessage()).contains("500");
    }

    @Test
    @DisplayName("헬스체크(/actuator)는 access log 대상이 아니다 — 30초마다 쌓인다")
    void givenActuatorRequest_whenShouldNotFilter_thenExcluded() {
        assertThat(filter.shouldNotFilter(request("GET", "/actuator/health"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/api/v1/users/me"))).isFalse();
    }
}
