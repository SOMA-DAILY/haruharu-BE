package org.son.haruharu.domain.auth.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserCredentialReader {

    private final UserCredentialRepository userCredentialRepository;

    public java.util.Optional<UserCredential> findByProviderTypeAndProviderId(String providerType, String providerId) {
        return userCredentialRepository.findByProviderTypeAndProviderId(providerType, providerId);
    }

    /** 탈퇴한 동일 소셜 계정이 threshold 이후에 삭제됐는지 — 재가입 차단 기간 내 여부. */
    public boolean existsWithdrawnWithin(String providerType, String providerId, java.time.LocalDateTime threshold) {
        return userCredentialRepository.existsWithdrawnWithin(providerType, providerId, threshold);
    }
}
