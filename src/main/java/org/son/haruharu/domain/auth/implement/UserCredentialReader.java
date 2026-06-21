package org.son.haruharu.domain.auth.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.auth.storage.exception.UserCredentialNotFoundException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserCredentialReader {

    private final UserCredentialRepository userCredentialRepository;

    public UserCredential getByProviderTypeAndProviderId(String providerType, String providerId) {
        return userCredentialRepository.findByProviderTypeAndProviderId(providerType, providerId)
                .orElseThrow(UserCredentialNotFoundException::new);
    }
}
