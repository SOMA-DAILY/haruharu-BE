package org.son.haruharu.domain.auth.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.auth.implement.UserCredential;
import org.son.haruharu.domain.auth.implement.UserCredentialRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    @Override
    public void deleteByUserId(Long userId) {
        userCredentialJpaRepository.deleteByUserId(userId);
    }

    @Override
    public boolean existsWithdrawnWithin(String providerType, String providerId, LocalDateTime threshold) {
        return userCredentialJpaRepository.countWithdrawnWithin(providerType, providerId, threshold) > 0;
    }

    @Override
    public int purgeWithdrawnBefore(String providerType, String providerId, LocalDateTime threshold) {
        return userCredentialJpaRepository.purgeWithdrawnBefore(providerType, providerId, threshold);
    }
}
