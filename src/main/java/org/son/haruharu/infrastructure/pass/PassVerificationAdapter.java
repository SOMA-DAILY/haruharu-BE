package org.son.haruharu.infrastructure.pass;

import org.son.haruharu.infrastructure.pass.exception.PassVerificationException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
        } catch (RestClientException e) {
            throw new PassVerificationException();
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
