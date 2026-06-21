package org.son.haruharu.domain.auth.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserCredentialJpaRepository extends JpaRepository<UserCredentialEntity, UUID> {

    Optional<UserCredentialEntity> findByProviderTypeAndProviderId(String providerType, String providerId);
}
