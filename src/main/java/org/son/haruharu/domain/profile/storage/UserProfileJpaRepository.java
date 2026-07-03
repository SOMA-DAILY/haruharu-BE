package org.son.haruharu.domain.profile.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileJpaRepository extends JpaRepository<UserProfileEntity, UUID> {

    Optional<UserProfileEntity> findByUserId(Long userId);

    List<UserProfileEntity> findByUserIdIn(Collection<Long> userIds);
}
