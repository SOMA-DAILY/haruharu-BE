package org.son.haruharu.domain.identity.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.identity.implement.UserIdentity;
import org.son.haruharu.domain.identity.implement.UserIdentityRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserIdentityRepositoryImpl implements UserIdentityRepository {

    private final UserIdentityJpaRepository jpaRepository;

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
        return jpaRepository.existsByCi(ci);
    }

    @Override
    public UserIdentity save(UserIdentity userIdentity) {
        return jpaRepository.save(UserIdentityEntity.from(userIdentity)).toDomain();
    }

    @Override
    public void deleteByUserId(Long userId) {
        jpaRepository.deleteByUserId(userId);
    }
}
