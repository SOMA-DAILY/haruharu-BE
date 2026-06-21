package org.son.haruharu.domain.auth.business.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthTokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("user_status") String userStatus
) {
    public static AuthTokenResponse withStatus(String accessToken, String refreshToken, String userStatus) {
        return new AuthTokenResponse(accessToken, refreshToken, userStatus);
    }

    public static AuthTokenResponse tokenOnly(String accessToken, String refreshToken) {
        return new AuthTokenResponse(accessToken, refreshToken, null);
    }
}
