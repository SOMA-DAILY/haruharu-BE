package org.son.haruharu.domain.profile.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserImageJpaRepository extends JpaRepository<UserImageEntity, UUID> {

    List<UserImageEntity> findAllByUserIdOrderBySortedOrderAsc(UUID userId);

    void deleteAllByUserId(UUID userId);
}
