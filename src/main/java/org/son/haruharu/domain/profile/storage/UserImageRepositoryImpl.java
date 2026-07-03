package org.son.haruharu.domain.profile.storage;

import org.son.haruharu.domain.profile.implement.UserImage;
import org.son.haruharu.domain.profile.implement.UserImageRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class UserImageRepositoryImpl implements UserImageRepository {

    private final UserImageJpaRepository userImageJpaRepository;

    public UserImageRepositoryImpl(UserImageJpaRepository userImageJpaRepository) {
        this.userImageJpaRepository = userImageJpaRepository;
    }

    @Override
    public List<UserImage> findAllByUserId(Long userId) {
        return userImageJpaRepository.findAllByUserIdOrderBySortedOrderAsc(userId)
                .stream()
                .map(UserImageEntity::toDomain)
                .toList();
    }

    @Override
    public UserImage save(UserImage userImage) {
        return userImageJpaRepository.save(UserImageEntity.from(userImage)).toDomain();
    }

    @Override
    public void deleteById(UUID id) {
        userImageJpaRepository.deleteById(id);
    }

    @Override
    public void deleteAllByUserId(Long userId) {
        userImageJpaRepository.deleteAllByUserId(userId);
    }
}
