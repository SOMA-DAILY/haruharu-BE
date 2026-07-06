package org.son.haruharu.infrastructure.pass;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.infrastructure.pass.exception.PassProviderException;
import org.son.haruharu.infrastructure.pass.exception.PassVerificationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PassVerificationAdapterTest {

    private static final String IV_ID = "identity-verification-123";
    private static final String EXPECTED_URI =
        "https://api.portone.io/identity-verifications/" + IV_ID;

    private MockRestServiceServer server;
    private PassVerificationAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        PortOneProperties properties = new PortOneProperties("store-1", "secret-1", "channel-1");
        adapter = new PassVerificationAdapter(properties, builder);
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
    @DisplayName("포트원 조회가 404로 실패하면 PassVerificationException이 발생한다")
    void givenNotFound_whenVerify_thenThrowsPassVerificationException() {
        server.expect(requestTo(EXPECTED_URI))
              .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> adapter.verify(IV_ID))
            .isInstanceOf(PassVerificationException.class);
    }

    @Test
    @DisplayName("포트원 서버 오류(5xx) 시 PassProviderException이 발생한다")
    void givenServerError_whenVerify_thenThrowsPassProviderException() {
        server.expect(requestTo(EXPECTED_URI))
              .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.verify(IV_ID))
            .isInstanceOf(PassProviderException.class);
    }
}
