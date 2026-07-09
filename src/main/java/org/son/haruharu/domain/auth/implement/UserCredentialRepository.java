package org.son.haruharu.domain.auth.implement;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserCredentialRepository {

    Optional<UserCredential> findByProviderTypeAndProviderId(String providerType, String providerId);

    UserCredential save(UserCredential userCredential);

    void deleteByUserId(Long userId);

    boolean existsWithdrawnWithin(String providerType, String providerId, LocalDateTime threshold);

    int purgeWithdrawnBefore(String providerType, String providerId, LocalDateTime threshold);
}
