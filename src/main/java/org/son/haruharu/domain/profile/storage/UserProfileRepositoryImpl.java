package org.son.haruharu.domain.profile.storage;

import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class UserProfileRepositoryImpl implements UserProfileRepository {

    private final UserProfileJpaRepository userProfileJpaRepository;

    public UserProfileRepositoryImpl(UserProfileJpaRepository userProfileJpaRepository) {
        this.userProfileJpaRepository = userProfileJpaRepository;
    }

    @Override
    public Optional<UserProfile> findByUserId(UUID userId) {
        return userProfileJpaRepository.findByUserId(userId)
                .map(UserProfileEntity::toDomain);
    }

    @Override
    public UserProfile save(UserProfile userProfile) {
        return userProfileJpaRepository.save(UserProfileEntity.from(userProfile)).toDomain();
    }
}
