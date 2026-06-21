package org.son.haruharu.domain.auth.implement;

import java.util.Optional;

public interface UserCredentialRepository {

    Optional<UserCredential> findByProviderTypeAndProviderId(String providerType, String providerId);

    UserCredential save(UserCredential userCredential);
}
