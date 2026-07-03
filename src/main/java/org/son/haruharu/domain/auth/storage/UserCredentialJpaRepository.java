package org.son.haruharu.domain.auth.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserCredentialJpaRepository extends JpaRepository<UserCredentialEntity, Long> {

    Optional<UserCredentialEntity> findByProviderTypeAndProviderId(String providerType, String providerId);
}
