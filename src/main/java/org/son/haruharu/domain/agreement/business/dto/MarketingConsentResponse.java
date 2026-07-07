package org.son.haruharu.domain.agreement.business.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.son.haruharu.domain.agreement.implement.UserAgreement;

import java.time.LocalDateTime;
import java.util.Optional;

public record MarketingConsentResponse(
        @JsonProperty("agreed") boolean agreed,
        @JsonProperty("version") String version,
        @JsonProperty("agreed_at") LocalDateTime agreedAt
) {
    public static MarketingConsentResponse from(Optional<UserAgreement> latest) {
        return latest
            .map(a -> new MarketingConsentResponse(a.agreed(), a.version(), a.agreedAt()))
            .orElseGet(() -> new MarketingConsentResponse(false, null, null));
    }
}
