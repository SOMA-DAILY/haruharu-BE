package org.son.haruharu.domain.auth.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.auth.implement.UserCredential;
import org.son.haruharu.domain.auth.implement.UserCredentialRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserCredentialRepositoryImpl implements UserCredentialRepository {

    private final UserCredentialJpaRepository userCredentialJpaRepository;

    @Override
    public Optional<UserCredential> findByProviderTypeAndProviderId(String providerType, String providerId) {
        return userCredentialJpaRepository.findByProviderTypeAndProviderId(providerType, providerId)
            .map(UserCredentialEntity::toDomain);
    }

    @Override
    public UserCredential save(UserCredential userCredential) {
        return userCredentialJpaRepository.save(UserCredentialEntity.from(userCredential)).toDomain();
    }
}
