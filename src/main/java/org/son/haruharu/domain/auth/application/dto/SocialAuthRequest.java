package org.son.haruharu.domain.auth.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import org.son.haruharu.domain.auth.business.command.SocialAuthCommand;
import org.son.haruharu.domain.auth.implement.ProviderType;

public record SocialAuthRequest(
    @JsonProperty("provider_type") @NotBlank String providerType,
    // TODO: 클라이언트 팀 확정 필요 — 이 필드에 OIDC idToken(JWT 전체) vs 소셜 sub 값 중 무엇을 담는지 합의 후 필드명 결정
    @JsonProperty("provider_id") @NotBlank String providerId
) {
    public SocialAuthCommand toCommand() {
        return new SocialAuthCommand(ProviderType.from(providerType), providerId);
    }
}
