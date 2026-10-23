package org.son.haruharu.handler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.support.LogCapture;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 실패 로그가 유출 경로가 되지 않는지 고정한다(#259 1-1, 2-2).
 * pgjdbc는 logServerErrorDetail 기본값 true라 서버 에러의 DETAIL(컬럼 실제 값)이 예외 메시지에 실린다.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private LogCapture logs;

    @BeforeEach
    void attachAppender() {
        // 클라 끊김을 DEBUG로 낮추는 것이 계약이라 DEBUG까지 봐야 한다
        logs = LogCapture.of(GlobalExceptionHandler.class).atLevel(Level.DEBUG);
    }

    @AfterEach
    void detachAppender() {
        logs.close();
    }

    private HttpServletRequest request(String method, String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        given(request.getMethod()).willReturn(method);
        given(request.getRequestURI()).willReturn(uri);
        return request;
    }

    @Test
    @DisplayName("업로드 용량 초과는 413을 반환하고 원문·스택 없이 WARN으로 기록한다")
    void givenOversizedUpload_whenHandle_thenLogsWarningWithoutCause() {
        var exception = new org.springframework.web.multipart.MaxUploadSizeExceededException(
                10 * 1024 * 1024, new IllegalStateException("private-upload-filename.png"));

        var response = handler.handleUploadTooLarge(exception,
                request("POST", "/api/v1/chat-rooms/1/photo-messages"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(logs.at(Level.ERROR)).isEmpty();
        assertThat(logs.at(Level.WARN)).hasSize(1);
        assertThat(logs.first().getThrowableProxy()).isNull();
        assertThat(logs.text()).contains("UPLOAD_TOO_LARGE").doesNotContain("private-upload-filename.png");
    }

    @Test
    @DisplayName("FCM 토큰 유니크 충돌 로그에 제약명만 남고 토큰 실제 값은 남지 않는다")
    void givenUniqueViolationWithDetail_whenHandle_thenLogsConstraintNameWithoutValue() {
        // given — pgjdbc가 logServerErrorDetail=true 로 DETAIL 을 실어 보낸 상황 (드라이버 설정이 누락된 환경)
        String pgMessage = """
                ERROR: duplicate key value violates unique constraint "ux_device_tokens_token"
                  Detail: Key (token)=(fcm-real-device-token-abcdef123456) already exists.""";
        var e = new DataIntegrityViolationException("could not execute statement", new RuntimeException(pgMessage));

        // when
        var response = handler.handleDataIntegrity(e, request("POST", "/api/v1/notifications/device-tokens"));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        String text = logs.text();
        assertThat(text).contains("ux_device_tokens_token");
        assertThat(text).doesNotContain("fcm-real-device-token-abcdef123456");
        assertThat(text).doesNotContain("Key (token)=");
    }

    @Test
    @DisplayName("실명인증 CI blind index 충돌 로그에 ci_hash 실제 값이 남지 않는다")
    void givenCiHashViolation_whenHandle_thenLogsConstraintNameWithoutValue() {
        // given
        String pgMessage = """
                ERROR: duplicate key value violates unique constraint "uq_user_identities_ci_hash_active"
                  Detail: Key (ci_hash)=(9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08) already exists.""";
        var e = new DataIntegrityViolationException("could not execute statement", new RuntimeException(pgMessage));

        // when
        handler.handleDataIntegrity(e, request("POST", "/api/v1/identity/verify"));

        // then
        String text = logs.text();
        assertThat(text).contains("uq_user_identities_ci_hash_active");
        assertThat(text).doesNotContain("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
    }

    @Test
    @DisplayName("제약명을 찾을 수 없는 무결성 위반은 원문 대신 고정 문구만 남긴다")
    void givenUnparseableViolation_whenHandle_thenLogsNoRawCause() {
        // given — 제약명이 없는 형태(체크 제약·FK 등)라도 원문을 그대로 흘리면 값이 샐 수 있다
        var e = new DataIntegrityViolationException("could not execute statement",
                new RuntimeException("ERROR: null value in column \"nickname\" violates not-null constraint\n  Detail: Failing row contains (1, 실명, 010-1234-5678)."));

        // when
        handler.handleDataIntegrity(e, request("POST", "/api/v1/profiles"));

        // then
        String text = logs.text();
        assertThat(text).doesNotContain("010-1234-5678");
        assertThat(text).doesNotContain("Failing row contains");
    }

    @Test
    @DisplayName("클라이언트가 응답 수신 도중 끊으면 ERROR·스택 없이 DEBUG 1줄만 남는다")
    void givenClientDisconnect_whenHandle_thenLogsDebugWithoutStack() {
        // given — /v3/api-docs 수신 중 탭을 닫은 상황
        var e = new org.springframework.web.context.request.async.AsyncRequestNotUsableException(
                "ServletOutputStream failed to write", new java.io.IOException("Broken pipe"));

        // when
        handler.handleException(e, request("GET", "/v3/api-docs"));

        // then
        assertThat(logs.at(Level.ERROR)).isEmpty();
        assertThat(logs.at(Level.DEBUG)).hasSize(1);
        assertThat(logs.first().getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("클라 끊김이 아닌 진짜 서버 예외는 기존대로 ERROR·스택으로 남는다")
    void givenRealServerError_whenHandle_thenLogsErrorWithStack() {
        // given
        var e = new IllegalStateException("bean not initialized");

        // when
        var response = handler.handleException(e, request("GET", "/api/v1/home"));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        List<ILoggingEvent> errors = logs.at(Level.ERROR);
        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst().getThrowableProxy()).isNotNull();
    }
}
