package org.son.haruharu.domain.profile.storage;

import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public class UserProfileRepositoryImpl implements UserProfileRepository {

    private final UserProfileJpaRepository userProfileJpaRepository;

    public UserProfileRepositoryImpl(UserProfileJpaRepository userProfileJpaRepository) {
        this.userProfileJpaRepository = userProfileJpaRepository;
    }

    @Override
    public Optional<UserProfile> findByUserId(Long userId) {
        return userProfileJpaRepository.findByUserId(userId)
                .map(UserProfileEntity::toDomain);
    }

    @Override
    public List<UserProfile> findByUserIdIn(Collection<Long> userIds) {
        return userProfileJpaRepository.findByUserIdIn(userIds).stream()
                .map(UserProfileEntity::toDomain)
                .toList();
    }

    @Override
    public UserProfile save(UserProfile userProfile) {
        return userProfileJpaRepository.save(UserProfileEntity.from(userProfile)).toDomain();
    }

    @Override
    public int updateApplicantContact(Long userId, String school, String department, String phone,
                                      LocalDateTime now) {
        return userProfileJpaRepository.updateApplicantContact(userId, school, department, phone, now);
    }

    @Override
    public void deleteByUserId(Long userId) {
        userProfileJpaRepository.deleteByUserId(userId);
    }
}
