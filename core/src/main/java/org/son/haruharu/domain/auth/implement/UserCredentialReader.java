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
}
