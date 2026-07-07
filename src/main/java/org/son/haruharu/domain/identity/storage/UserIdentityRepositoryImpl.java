package org.son.haruharu.domain.identity.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.common.crypto.IdentityCrypto;
import org.son.haruharu.domain.identity.implement.UserIdentity;
import org.son.haruharu.domain.identity.implement.UserIdentityRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserIdentityRepositoryImpl implements UserIdentityRepository {

    private final UserIdentityJpaRepository jpaRepository;
    private final IdentityCrypto crypto;

    @Override
    public Optional<UserIdentity> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId).map(UserIdentityEntity::toDomain);
    }

    @Override
    public boolean existsByUserId(Long userId) {
        return jpaRepository.existsByUserId(userId);
    }

    @Override
    public boolean existsByCi(String ci) {
        return jpaRepository.existsByCiHash(crypto.blindIndex(ci));
    }

    @Override
    public UserIdentity save(UserIdentity userIdentity) {
        String ciHash = crypto.blindIndex(userIdentity.ci());
        return jpaRepository.save(UserIdentityEntity.from(userIdentity, ciHash)).toDomain();
    }

    @Override
    public void deleteByUserId(Long userId) {
        jpaRepository.deleteByUserId(userId);
    }

    @Override
    public boolean existsWithdrawnByCiWithin(String ci, LocalDateTime threshold) {
        return jpaRepository.countWithdrawnByCiHashAfter(crypto.blindIndex(ci), threshold) > 0;
    }

    @Override
    public void purgeWithdrawnByCiBefore(String ci, LocalDateTime threshold) {
        jpaRepository.purgeWithdrawnByCiHashBefore(crypto.blindIndex(ci), threshold);
    }

    @Override
    public int purgeWithdrawnBefore(LocalDateTime threshold) {
        return jpaRepository.purgeWithdrawnBefore(threshold);
    }
}
