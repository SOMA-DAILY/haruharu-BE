package org.son.haruharu.infrastructure.pass;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.son.haruharu.infrastructure.pass.exception.PassProviderException;
import org.son.haruharu.infrastructure.pass.exception.PassVerificationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PassVerificationAdapterTest {

    private static final String IV_ID = "identity-verification-123";
    private static final String EXPECTED_URI =
        "https://api.portone.io/identity-verifications/" + IV_ID;

    private MockRestServiceServer server;
    private PassVerificationAdapter adapter;
    private Logger adapterLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        PortOneProperties properties = new PortOneProperties("store-1", "secret-1", "channel-1");
        adapter = new PassVerificationAdapter(properties, builder);

        adapterLogger = (Logger) LoggerFactory.getLogger(PassVerificationAdapter.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        adapterLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        adapterLogger.detachAppender(logAppender);
    }

    private ILoggingEvent lastLog() {
        assertThat(logAppender.list).isNotEmpty();
        return logAppender.list.getLast();
    }

    @Test
    @DisplayName("VERIFIED 응답을 받으면 verifiedCustomer가 PassVerificationResult로 매핑된다")
    void givenVerifiedResponse_whenVerify_thenMapsToResult() {
        String body = """
            {
              "status": "VERIFIED",
              "verifiedCustomer": {
                "ci": "ci-value",
                "di": "di-value",
                "name": "홍길동",
                "birthDate": "1998-05-15",
                "gender": "MALE",
                "phoneNumber": "01012345678",
                "operator": "SKT",
                "isForeigner": false
              }
            }
            """;
        server.expect(requestTo(EXPECTED_URI))
              .andExpect(method(HttpMethod.GET))
              .andExpect(header(HttpHeaders.AUTHORIZATION, "PortOne secret-1"))
              .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        PassVerificationResult result = adapter.verify(IV_ID);

        assertThat(result.ci()).isEqualTo("ci-value");
        assertThat(result.di()).isEqualTo("di-value");
        assertThat(result.userName()).isEqualTo("홍길동");
        assertThat(result.birthday()).isEqualTo(LocalDate.of(1998, 5, 15));
        assertThat(result.gender()).isEqualTo("MALE");
        assertThat(result.phoneNumber()).isEqualTo("01012345678");
        assertThat(result.operator()).isEqualTo("SKT");
        assertThat(result.isForeigner()).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("다날 응답에 phoneNumber·operator가 없어도 정상 매핑된다 (통신사 미승인 채널)")
    void givenDanalResponseWithoutPhone_whenVerify_thenMapsWithNullPhoneAndOperator() {
        // 통신사 사전승인 전 다날(DANAL) 채널은 phoneNumber·operator·isForeigner 키를 응답에 담지 않는다.
        String body = """
            {
              "status": "VERIFIED",
              "id": "identity-verification-123",
              "channel": { "type": "LIVE", "name": "다날", "pgProvider": "DANAL" },
              "verifiedCustomer": {
                "id": "port-customer-id-xxx",
                "name": "홍길동",
                "birthDate": "1998-05-15",
                "gender": "MALE",
                "ci": "ci-value",
                "di": "di-value"
              },
              "version": "V2"
            }
            """;
        server.expect(requestTo(EXPECTED_URI))
              .andExpect(method(HttpMethod.GET))
              .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        PassVerificationResult result = adapter.verify(IV_ID);

        assertThat(result.ci()).isEqualTo("ci-value");
        assertThat(result.di()).isEqualTo("di-value");
        assertThat(result.userName()).isEqualTo("홍길동");
        assertThat(result.birthday()).isEqualTo(LocalDate.of(1998, 5, 15));
        assertThat(result.gender()).isEqualTo("MALE");
        assertThat(result.phoneNumber()).isNull();
        assertThat(result.operator()).isNull();
        assertThat(result.isForeigner()).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("status가 VERIFIED가 아니면 PassVerificationException이 발생한다")
    void givenNonVerifiedStatus_whenVerify_thenThrowsPassVerificationException() {
        String body = """
            { "status": "FAILED", "verifiedCustomer": null }
            """;
        server.expect(requestTo(EXPECTED_URI))
              .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.verify(IV_ID))
            .isInstanceOf(PassVerificationException.class);
    }

    @Test
    @DisplayName("VERIFIED이지만 필수값(ci)이 누락되면 PassVerificationException이 발생한다")
    void givenVerifiedButMissingRequiredField_whenVerify_thenThrowsPassVerificationException() {
        String body = """
            {
              "status": "VERIFIED",
              "verifiedCustomer": {
                "ci": null,
                "di": "di-value",
                "name": "홍길동",
                "birthDate": "1998-05-15",
                "gender": "MALE",
                "phoneNumber": "01012345678",
                "operator": "SKT",
                "isForeigner": false
              }
            }
            """;
        server.expect(requestTo(EXPECTED_URI))
              .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.verify(IV_ID))
            .isInstanceOf(PassVerificationException.class);
    }

    @Test
    @DisplayName("포트원 조회가 404로 실패하면 PassVerificationException이 발생하고 warn 로그에 식별ID가 남는다")
    void givenNotFound_whenVerify_thenThrowsPassVerificationExceptionAndWarnLog() {
        server.expect(requestTo(EXPECTED_URI))
              .andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"type\":\"NOT_FOUND\"}"));

        assertThatThrownBy(() -> adapter.verify(IV_ID))
            .isInstanceOf(PassVerificationException.class);

        ILoggingEvent log = lastLog();
        assertThat(log.getLevel()).isEqualTo(Level.WARN);
        assertThat(log.getFormattedMessage())
            .contains(IV_ID)
            .contains("404");
    }

    @Test
    @DisplayName("포트원 조회가 400으로 실패하면 PassVerificationException이 발생한다")
    void givenBadRequest_whenVerify_thenThrowsPassVerificationException() {
        server.expect(requestTo(EXPECTED_URI))
              .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> adapter.verify(IV_ID))
            .isInstanceOf(PassVerificationException.class);

        assertThat(lastLog().getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    @DisplayName("포트원 조회가 401이면 PassProviderException + API Secret 오류 진단이 error 로그에 남는다")
    void givenUnauthorized_whenVerify_thenThrowsPassProviderExceptionAndLogsSecretHint() {
        server.expect(requestTo(EXPECTED_URI))
              .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"type\":\"UNAUTHORIZED\"}"));

        assertThatThrownBy(() -> adapter.verify(IV_ID))
            .isInstanceOf(PassProviderException.class);

        ILoggingEvent log = lastLog();
        assertThat(log.getLevel()).isEqualTo(Level.ERROR);
        assertThat(log.getFormattedMessage())
            .contains("401")
            .contains("PORTONE_V2_API_SECRET");
    }

    @Test
    @DisplayName("포트원 조회가 403이면 PassProviderException + 인증정보 진단이 error 로그에 남는다")
    void givenForbidden_whenVerify_thenThrowsPassProviderExceptionAndLogsSecretHint() {
        server.expect(requestTo(EXPECTED_URI))
              .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> adapter.verify(IV_ID))
            .isInstanceOf(PassProviderException.class);

        ILoggingEvent log = lastLog();
        assertThat(log.getLevel()).isEqualTo(Level.ERROR);
        assertThat(log.getFormattedMessage()).contains("403");
    }

    @Test
    @DisplayName("포트원 서버 오류(5xx) 시 PassProviderException + 포트원 서버 오류 진단이 error 로그에 남는다")
    void givenServerError_whenVerify_thenThrowsPassProviderExceptionAndErrorLog() {
        server.expect(requestTo(EXPECTED_URI))
              .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.verify(IV_ID))
            .isInstanceOf(PassProviderException.class);

        ILoggingEvent log = lastLog();
        assertThat(log.getLevel()).isEqualTo(Level.ERROR);
        assertThat(log.getFormattedMessage()).contains("500");
    }

    @Test
    @DisplayName("네트워크 오류(타임아웃) 시 PassProviderException + 네트워크 원인이 error 로그에 남는다")
    void givenNetworkTimeout_whenVerify_thenThrowsPassProviderExceptionAndLogsCause() {
        server.expect(requestTo(EXPECTED_URI))
              .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> adapter.verify(IV_ID))
            .isInstanceOf(PassProviderException.class);

        ILoggingEvent log = lastLog();
        assertThat(log.getLevel()).isEqualTo(Level.ERROR);
        assertThat(log.getFormattedMessage())
            .contains(IV_ID)
            .contains("네트워크");
        assertThat(log.getFormattedMessage()).containsIgnoringCase("SocketTimeoutException");
    }
}
