package org.son.haruharu.domain.user.business.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OnboardingStatusResponse(
        @JsonProperty("user_status") String userStatus
) {}
