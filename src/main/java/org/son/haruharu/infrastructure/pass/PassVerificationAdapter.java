package org.son.haruharu.infrastructure.pass;

import org.son.haruharu.infrastructure.pass.exception.PassProviderException;
import org.son.haruharu.infrastructure.pass.exception.PassVerificationException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Component
public class PassVerificationAdapter implements PassVerificationPort {

    private static final String BASE_URL = "https://api.portone.io";
    private static final String VERIFIED_STATUS = "VERIFIED";

    private final RestClient restClient;
    private final PortOneProperties properties;

    public PassVerificationAdapter(PortOneProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.baseUrl(BASE_URL).build();
    }

    @Override
    public PassVerificationResult verify(String identityVerificationId) {
        PortOneIdentityResponse response = fetch(identityVerificationId);
        if (response == null
                || !VERIFIED_STATUS.equals(response.status())
                || response.verifiedCustomer() == null) {
            throw new PassVerificationException();
        }
        return toResult(response.verifiedCustomer());
    }

    private PortOneIdentityResponse fetch(String identityVerificationId) {
        try {
            return restClient.get()
                    .uri("/identity-verifications/{id}", identityVerificationId)
                    .header(HttpHeaders.AUTHORIZATION, "PortOne " + properties.apiSecret())
                    .retrieve()
                    .body(PortOneIdentityResponse.class);
        } catch (RestClientResponseException e) {
            // 400/404: 클라이언트가 넘긴 identityVerificationId 가 잘못되거나 만료됨 → 사용자 재시도 대상
            int status = e.getStatusCode().value();
            if (status == 400 || status == 404) {
                throw new PassVerificationException();
            }
            // 401/403(인증정보 오류)·5xx: 포트원/서버 측 문제
            throw new PassProviderException();
        } catch (RestClientException e) {
            // 네트워크 오류·타임아웃
            throw new PassProviderException();
        }
    }

    private PassVerificationResult toResult(PortOneIdentityResponse.VerifiedCustomer c) {
        try {
            return new PassVerificationResult(
                    c.ci(), c.di(), c.phoneNumber(),
                    LocalDate.parse(c.birthDate()),
                    c.gender(), c.name(), c.operator(), c.isForeigner()
            );
        } catch (DateTimeParseException | NullPointerException e) {
            throw new PassVerificationException();
        }
    }
}
