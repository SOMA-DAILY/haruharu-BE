package org.son.haruharu.infrastructure.pass;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.infrastructure.pass.exception.PassProviderException;
import org.son.haruharu.infrastructure.pass.exception.PassVerificationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Slf4j
@Component
public class PassVerificationAdapter implements PassVerificationPort {

    private static final String BASE_URL = "https://api.portone.io";
    private static final String VERIFIED_STATUS = "VERIFIED";
    // 실패 응답 바디에 CI/DI 등 개인정보가 포함될 가능성에 대비해 로깅 길이를 제한한다.
    private static final int MAX_LOGGED_BODY_LENGTH = 500;

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
            // 포트원이 HTTP 상태코드를 반환한 경우 — 상태코드별로 원인을 구분해 진단 로그를 남긴다.
            return handleResponseError(identityVerificationId, e);
        } catch (ResourceAccessException e) {
            // 커넥션 실패·읽기 타임아웃 등 I/O 오류 — 포트원 REST 에 도달조차 못한 경우.
            Throwable root = e.getMostSpecificCause();
            log.error("포트원 본인인증 조회 실패 - 네트워크 오류(포트원 REST 도달 실패, 타임아웃/커넥션). ivId={}, cause={}: {}",
                    identityVerificationId, root.getClass().getSimpleName(), root.getMessage());
            throw new PassProviderException();
        } catch (RestClientException e) {
            // 그 외 RestClient 계층 오류 — 응답 역직렬화 실패 등.
            Throwable root = e.getMostSpecificCause();
            log.error("포트원 본인인증 조회 실패 - 응답 처리 오류(역직렬화/기타). ivId={}, cause={}: {}",
                    identityVerificationId, root.getClass().getSimpleName(), root.getMessage());
            throw new PassProviderException();
        }
    }

    private PortOneIdentityResponse handleResponseError(String ivId, RestClientResponseException e) {
        HttpStatusCode status = e.getStatusCode();
        String body = truncateBody(e.getResponseBodyAsString());

        if (status.value() == 400 || status.value() == 404) {
            // 클라이언트가 넘긴 identityVerificationId 가 잘못되거나 만료됨 → 사용자 재시도 대상.
            log.warn("포트원 본인인증 조회 실패 - 유효하지 않거나 만료된 식별ID(사용자 재시도 대상). status={}, ivId={}, body={}",
                    status.value(), ivId, body);
            throw new PassVerificationException();
        }
        if (status.value() == 401 || status.value() == 403) {
            // 포트원 인증정보(API Secret) 거부 → 서버 설정 문제. 운영 환경변수 확인 필요.
            log.error("포트원 본인인증 조회 실패 - 인증정보 거부(API Secret 오류 의심). PORTONE_V2_API_SECRET 값을 확인하세요. status={}, ivId={}, body={}",
                    status.value(), ivId, body);
            throw new PassProviderException();
        }
        if (status.is5xxServerError()) {
            // 포트원 서버 장애.
            log.error("포트원 본인인증 조회 실패 - 포트원 서버 오류(5xx). status={}, ivId={}, body={}",
                    status.value(), ivId, body);
            throw new PassProviderException();
        }
        // 그 외 예상치 못한 4xx (405·409·429 등).
        log.error("포트원 본인인증 조회 실패 - 예상치 못한 응답. status={}, ivId={}, body={}",
                status.value(), ivId, body);
        throw new PassProviderException();
    }

    private static String truncateBody(String body) {
        if (body == null || body.isBlank()) {
            return "<empty>";
        }
        return body.length() <= MAX_LOGGED_BODY_LENGTH
                ? body
                : body.substring(0, MAX_LOGGED_BODY_LENGTH) + "...(truncated)";
    }

    private PassVerificationResult toResult(PortOneIdentityResponse.VerifiedCustomer c) {
        // phoneNumber·operator 는 통신사 사전승인을 받은 채널에서만 내려온다(다날 미승인 시 부재).
        // 신원 확정에 필수인 값은 ci·di·name·birthDate·gender 이므로 이들만 검증한다.
        if (c.ci() == null || c.di() == null || c.name() == null
                || c.birthDate() == null || c.gender() == null) {
            throw new PassVerificationException();
        }
        try {
            return new PassVerificationResult(
                    c.ci(), c.di(), c.phoneNumber(),
                    LocalDate.parse(c.birthDate()),
                    c.gender(), c.name(), c.operator(), Boolean.TRUE.equals(c.isForeigner())
            );
        } catch (DateTimeParseException e) {
            throw new PassVerificationException();
        }
    }
}
